package com.nimbleways.springboilerplate.controllers;

import com.nimbleways.springboilerplate.contollers.MyController;
import com.nimbleways.springboilerplate.dto.product.ProcessOrderResponse;
import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.NotificationService;
import com.nimbleways.springboilerplate.services.implementations.ProductService;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.nimbleways.springboilerplate.utils.TestClock.FIXED_CLOCK;
import static com.nimbleways.springboilerplate.utils.TestClock.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessOrderTests {
    private static final Long ORDER_ID = 42L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private NotificationService notificationService;

    private MyController controller;

    @BeforeEach
    void setUpController() {
        ProductService productService = new ProductService(productRepository, notificationService, FIXED_CLOCK);
        controller = new MyController(productService, productRepository, orderRepository, FIXED_CLOCK);
    }

    @Nested
    class NormalProducts {
        @ParameterizedTest(name = "NORMAL stock {0} becomes {1} without notification")
        @CsvSource({"5, 4", "1, 0"})
        void consumesOneUnitWithoutNotification(int stock, int expectedStock) {
            Product product = normal(stock, 15);

            process(product);

            assertEquals(expectedStock, product.getAvailable());
            assertEquals(15, product.getLeadTime());
            verify(productRepository).save(product);
            verifyNoInteractions(notificationService);
        }

        @ParameterizedTest
        @CsvSource({"0, 1", "0, 15", "-2, 7"})
        void outOfStockAnnouncesDelay(int stock, int leadTime) {
            Product product = normal(stock, leadTime);

            process(product);

            assertEquals(stock, product.getAvailable());
            assertDelay(product, leadTime);
        }

        @ParameterizedTest
        @CsvSource({"0, 0", "0, -3", "-2, 0", "-2, -3"})
        void outOfStockWithNonpositiveLeadTimeDoesNothing(int stock, int leadTime) {
            Product product = normal(stock, leadTime);

            process(product);

            assertEquals(stock, product.getAvailable());
            assertEquals(leadTime, product.getLeadTime());
            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void availableStockDoesNotRequireLeadTime() {
            Product product = normal(2, 15);
            product.setLeadTime(null);

            process(product);

            assertEquals(1, product.getAvailable());
            verify(productRepository).save(product);
            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    class SeasonalProducts {
        @ParameterizedTest
        @ValueSource(ints = {1, 5})
        void availableStockIsSoldDespiteLateRestock(int stock) {
            Product product = seasonal(stock, 30, TODAY.minusDays(1), TODAY.plusDays(1));

            process(product);

            assertEquals(stock - 1, product.getAvailable());
            verify(productRepository).save(product);
            verifyNoInteractions(notificationService);
        }

        @ParameterizedTest
        @CsvSource({"0, 5", "0, 10", "-2, 5", "0, 0", "0, -1"})
        void restockBySeasonEndAnnouncesDelay(int stock, int leadTime) {
            Product product = seasonal(stock, leadTime, TODAY.minusDays(5), TODAY.plusDays(10));

            process(product);

            assertEquals(stock, product.getAvailable());
            assertDelay(product, leadTime);
        }

        @Test
        void restockAfterSeasonEndNotifiesUnavailable() {
            Product product = seasonal(0, 11, TODAY.minusDays(5), TODAY.plusDays(10));

            process(product);

            assertOutOfStock(product, 0);
        }

        @Test
        void beforeSeasonNotifiesAndKeepsStock() {
            Product product = seasonal(7, 5, TODAY.plusDays(10), TODAY.plusDays(20));

            process(product);

            assertOutOfStock(product, 7);
        }

        @Test
        void lateRestockClearsStockEvenBeforeSeason() {
            Product product = seasonal(7, 21, TODAY.plusDays(10), TODAY.plusDays(20));

            process(product);

            assertOutOfStock(product, 0);
        }

        @Test
        void afterSeasonClearsStock() {
            Product product = seasonal(7, 5, TODAY.minusDays(20), TODAY.minusDays(1));

            process(product);

            assertOutOfStock(product, 0);
        }

        @Test
        void seasonStartAnnouncesDelayAndKeepsStock() {
            Product product = seasonal(7, 5, TODAY, TODAY.plusDays(20));

            process(product);

            assertEquals(7, product.getAvailable());
            assertDelay(product, 5);
        }

        @Test
        void seasonEndWithPositiveLeadTimeClearsStock() {
            Product product = seasonal(7, 1, TODAY.minusDays(20), TODAY);

            process(product);

            assertOutOfStock(product, 0);
        }

        @Test
        void seasonEndWithZeroLeadTimeAnnouncesDelay() {
            Product product = seasonal(7, 0, TODAY.minusDays(20), TODAY);

            process(product);

            assertEquals(7, product.getAvailable());
            assertDelay(product, 0);
        }
    }

    @Nested
    class ExpirableProducts {
        @ParameterizedTest
        @CsvSource({"5, 1", "1, 1", "5, 30"})
        void consumesUnexpiredStock(int stock, int daysUntilExpiry) {
            Product product = expirable(stock, TODAY.plusDays(daysUntilExpiry));

            process(product);

            assertEquals(stock - 1, product.getAvailable());
            verify(productRepository).save(product);
            verifyNoInteractions(notificationService);
        }

        @ParameterizedTest(name = "EXPIRABLE stock {0}, expiry offset {1}: expiration notification")
        @CsvSource({"5, -1", "5, 0", "0, 1", "-2, 1", "0, -1", "0, 0"})
        void expiredOrOutOfStockSendsExpirationNotification(int stock, int expiryOffset) {
            Product product = expirable(stock, TODAY.plusDays(expiryOffset));

            process(product);

            assertEquals(0, product.getAvailable());
            verify(productRepository).save(product);
            verify(notificationService).sendExpirationNotification(product.getName(), product.getExpiryDate());
            verifyNoMoreInteractions(notificationService);
        }

        @Test
        void zeroStockWithMissingExpiryStillNotifies() {
            Product product = expirable(0, null);

            process(product);

            assertEquals(0, product.getAvailable());
            verify(productRepository).save(product);
            verify(notificationService).sendExpirationNotification(product.getName(), null);
            verifyNoMoreInteractions(notificationService);
        }
    }

    @Nested
    class Orders {
        @Test
        void continuesAfterUnavailableProduct() {
            Product expired = expirable(4, TODAY.minusDays(1));
            Product normal = normal(3, 15);
            Product seasonal = seasonal(2, 5, TODAY.minusDays(1), TODAY.plusDays(10));

            process(expired, normal, seasonal);

            assertEquals(0, expired.getAvailable());
            assertEquals(2, normal.getAvailable());
            assertEquals(1, seasonal.getAvailable());
            verify(productRepository).save(expired);
            verify(productRepository).save(normal);
            verify(productRepository).save(seasonal);
            verify(notificationService).sendExpirationNotification(expired.getName(), expired.getExpiryDate());
            verifyNoMoreInteractions(notificationService);
        }

        @Test
        void emptyOrderReturnsId() {
            ProcessOrderResponse response = process();

            assertEquals(ORDER_ID, response.id());
            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void duplicateProductIsConsumedOnce() {
            Product product = normal(3, 15);

            process(product, product);

            assertEquals(2, product.getAvailable());
            verify(productRepository).save(product);
            verifyNoInteractions(notificationService);
        }

        @Test
        void repeatedOrderConsumesAnotherUnit() {
            Product product = normal(3, 15);
            givenOrder(product);

            assertEquals(ORDER_ID, controller.processOrder(ORDER_ID).id());
            assertEquals(2, product.getAvailable());
            assertEquals(ORDER_ID, controller.processOrder(ORDER_ID).id());
            assertEquals(1, product.getAvailable());
            verifyNoInteractions(notificationService);
        }

        @ParameterizedTest
        @ValueSource(strings = {"UNKNOWN", "normal", "", " NORMAL "})
        void unknownTypesAreIgnored(String type) {
            Product product = normal(3, 15);
            product.setType(type);

            process(product);

            assertEquals(3, product.getAvailable());
            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void missingOrderThrows() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void saveFailureStopsRemainingProducts() {
            Product first = normal(3, 15);
            Product second = expirable(4, TODAY.plusDays(1));
            givenOrder(first, second);
            RuntimeException failure = new IllegalStateException("Database unavailable");
            when(productRepository.save(first)).thenThrow(failure);

            assertSame(failure, assertThrows(IllegalStateException.class, () -> controller.processOrder(ORDER_ID)));

            assertEquals(4, second.getAvailable());
            verify(productRepository).save(first);
            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    class InvalidInput {
        @Test
        void nullItemsThrow() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(new Order(ORDER_ID, null)));

            assertThrows(NullPointerException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void nullProductThrows() {
            givenOrder((Product) null);

            assertThrows(NullPointerException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void nullTypeThrows() {
            Product product = normal(0, 15);
            product.setType(null);
            givenOrder(product);

            assertThrows(NullPointerException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void nullStockThrows() {
            Product product = normal(0, 15);
            product.setAvailable(null);
            givenOrder(product);

            assertThrows(NullPointerException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void nullLeadTimeWhenOutOfStockThrows() {
            Product product = normal(0, 15);
            product.setLeadTime(null);
            givenOrder(product);

            assertThrows(NullPointerException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void nullSeasonStartThrows() {
            Product product = seasonal(2, 5, TODAY.minusDays(1), TODAY.plusDays(10));
            product.setSeasonStartDate(null);
            givenOrder(product);

            assertThrows(NullPointerException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void nullSeasonEndThrows() {
            Product product = seasonal(2, 5, TODAY.minusDays(1), TODAY.plusDays(10));
            product.setSeasonEndDate(null);
            givenOrder(product);

            assertThrows(NullPointerException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }

        @Test
        void missingExpiryWithStockThrows() {
            givenOrder(expirable(2, null));

            assertThrows(NullPointerException.class, () -> controller.processOrder(ORDER_ID));

            verifyNoInteractions(productRepository, notificationService);
        }
    }

    private ProcessOrderResponse process(Product... products) {
        givenOrder(products);
        return controller.processOrder(ORDER_ID);
    }

    private void givenOrder(Product... products) {
        Order order = new Order(ORDER_ID, new LinkedHashSet<>(Arrays.asList(products)));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
    }

    private void assertDelay(Product product, int leadTime) {
        assertEquals(leadTime, product.getLeadTime());
        verify(productRepository).save(product);
        verify(notificationService).sendDelayNotification(leadTime, product.getName());
        verifyNoMoreInteractions(notificationService);
    }

    private void assertOutOfStock(Product product, int expectedStock) {
        assertEquals(expectedStock, product.getAvailable());
        verify(productRepository).save(product);
        verify(notificationService).sendOutOfStockNotification(product.getName());
        verifyNoMoreInteractions(notificationService);
    }

    private static Product normal(int stock, int leadTime) {
        return new Product(null, leadTime, stock, "NORMAL", "USB Cable", null, null, null);
    }

    private static Product seasonal(int stock, int leadTime, LocalDate start, LocalDate end) {
        return new Product(null, leadTime, stock, "SEASONAL", "Watermelon", null, start, end);
    }

    private static Product expirable(int stock, LocalDate expiry) {
        return new Product(null, 15, stock, "EXPIRABLE", "Milk", expiry, null, null);
    }
}
