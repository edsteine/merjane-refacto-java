package com.nimbleways.springboilerplate.services.implementations;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {
    @Mock
    private NotificationService notificationService;
    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, notificationService, FIXED_CLOCK);
    }

    @Nested
    class DelayNotifications {
        @ParameterizedTest
        @ValueSource(ints = {7, 0, -2})
        void updatesLeadTimeAndNotifies(int leadTime) {
            Product product = new Product(null, 15, 3, "NORMAL", "RJ45 Cable", null, null, null);

            productService.notifyDelay(leadTime, product);

            assertEquals(3, product.getAvailable());
            assertEquals(leadTime, product.getLeadTime());
            verify(productRepository).save(product);
            verify(notificationService).sendDelayNotification(leadTime, product.getName());
            verifyNoMoreInteractions(notificationService);
        }

        @Test
        void saveFailurePreventsNotification() {
            Product product = new Product(null, 15, 0, "NORMAL", "RJ45 Cable", null, null, null);
            RuntimeException failure = new IllegalStateException("Database unavailable");
            when(productRepository.save(product)).thenThrow(failure);

            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> productService.notifyDelay(7, product)));

            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    class SeasonalProducts {
        @ParameterizedTest
        @CsvSource({"0, 5", "4, 5", "0, 10", "0, 0", "0, -1"})
        void restockBySeasonEndAnnouncesDelay(int stock, int leadTime) {
            Product product = new Product(null, leadTime, stock, "SEASONAL", "Watermelon", null,
                    TODAY.minusDays(5), TODAY.plusDays(10));

            productService.handleSeasonalProduct(product);

            assertEquals(stock, product.getAvailable());
            assertEquals(leadTime, product.getLeadTime());
            verify(productRepository).save(product);
            verify(notificationService).sendDelayNotification(leadTime, product.getName());
            verifyNoMoreInteractions(notificationService);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 4, -2})
        void restockAfterSeasonClearsStock(int stock) {
            Product product = new Product(null, 11, stock, "SEASONAL", "Watermelon", null,
                    TODAY.minusDays(5), TODAY.plusDays(10));

            productService.handleSeasonalProduct(product);

            assertEquals(0, product.getAvailable());
            verify(productRepository).save(product);
            verify(notificationService).sendOutOfStockNotification(product.getName());
            verifyNoMoreInteractions(notificationService);
        }

        @Test
        void beforeSeasonKeepsStock() {
            Product product = new Product(null, 5, 4, "SEASONAL", "Watermelon", null,
                    TODAY.plusDays(10), TODAY.plusDays(20));

            productService.handleSeasonalProduct(product);

            assertEquals(4, product.getAvailable());
            verify(productRepository).save(product);
            verify(notificationService).sendOutOfStockNotification(product.getName());
            verifyNoMoreInteractions(notificationService);
        }

        @Test
        void notificationFailureLeavesStockUnchanged() {
            Product product = new Product(null, 11, 4, "SEASONAL", "Watermelon", null,
                    TODAY.minusDays(5), TODAY.plusDays(10));
            RuntimeException failure = new IllegalStateException("Notification unavailable");
            doThrow(failure).when(notificationService).sendOutOfStockNotification(product.getName());

            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> productService.handleSeasonalProduct(product)));

            assertEquals(4, product.getAvailable());
            verifyNoInteractions(productRepository);
        }
    }

    @Nested
    class ExpirableProducts {
        @ParameterizedTest
        @ValueSource(ints = {1, 4})
        void consumesUnexpiredStock(int stock) {
            Product product = new Product(null, 15, stock, "EXPIRABLE", "Milk", TODAY.plusDays(1), null, null);

            productService.handleExpiredProduct(product);

            assertEquals(stock - 1, product.getAvailable());
            verify(productRepository).save(product);
            verifyNoInteractions(notificationService);
        }

        @ParameterizedTest
        @CsvSource({"4, -1", "4, 0", "0, 1", "-2, 1"})
        void expiredOrOutOfStockNotifiesAndClearsStock(int stock, int expiryOffset) {
            Product product = new Product(null, 15, stock, "EXPIRABLE", "Milk", TODAY.plusDays(expiryOffset), null, null);

            productService.handleExpiredProduct(product);

            assertEquals(0, product.getAvailable());
            verify(productRepository).save(product);
            verify(notificationService).sendExpirationNotification(product.getName(), product.getExpiryDate());
            verifyNoMoreInteractions(notificationService);
        }

        @Test
        void notificationFailureLeavesStockUnchanged() {
            Product product = new Product(null, 15, 4, "EXPIRABLE", "Milk", TODAY.minusDays(1), null, null);
            RuntimeException failure = new IllegalStateException("Notification unavailable");
            doThrow(failure).when(notificationService).sendExpirationNotification(product.getName(), product.getExpiryDate());

            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> productService.handleExpiredProduct(product)));

            assertEquals(4, product.getAvailable());
            verifyNoInteractions(productRepository);
        }
    }
}
