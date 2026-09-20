package com.nimbleways.springboilerplate.controllers;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.services.implementations.NotificationService;

import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.NestedServletException;

import static com.nimbleways.springboilerplate.utils.TestClock.FIXED_CLOCK;
import static com.nimbleways.springboilerplate.utils.TestClock.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MyControllerIntegrationTests.FixedClockConfiguration.class)
class MyControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private NotificationService notificationService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void clearDatabase() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void mixedOrderUpdatesStockAndNotifies() throws Exception {
        Product cable = new Product(null, 15, 30, "NORMAL", "USB Cable", null, null, null);
        Product dongle = new Product(null, 10, 0, "NORMAL", "USB Dongle", null, null, null);
        Product butter = new Product(null, 15, 30, "EXPIRABLE", "Butter", TODAY.plusDays(26), null, null);
        Product milk = new Product(null, 90, 6, "EXPIRABLE", "Milk", TODAY.minusDays(2), null, null);
        Product watermelon = new Product(null, 15, 30, "SEASONAL", "Watermelon", null,
                TODAY.minusDays(2), TODAY.plusDays(58));
        Product grapes = new Product(null, 15, 30, "SEASONAL", "Grapes", null,
                TODAY.plusDays(180), TODAY.plusDays(240));
        Order order = saveOrder(cable, dongle, butter, milk, watermelon, grapes);

        processOrder(order);

        assertStock(cable, 29);
        assertStock(dongle, 0);
        assertStock(butter, 29);
        assertStock(milk, 0);
        assertStock(watermelon, 29);
        assertStock(grapes, 30);
        assertEquals(10, reload(dongle).getLeadTime());
        verify(notificationService).sendDelayNotification(10, "USB Dongle");
        verify(notificationService).sendExpirationNotification("Milk", TODAY.minusDays(2));
        verify(notificationService).sendOutOfStockNotification("Grapes");
        verifyNoMoreInteractions(notificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NORMAL", "SEASONAL", "EXPIRABLE"})
    void lastUnitIsSoldWithoutNotification(String type) throws Exception {
        Product product = new Product(null, 5, 1, type, "Last item", TODAY.plusDays(1),
                TODAY.minusDays(1), TODAY.plusDays(10));
        Order order = saveOrder(product);

        processOrder(order);

        assertStock(product, 0);
        verifyNoInteractions(notificationService);
    }

    @Test
    void replenishmentAfterSeasonPersistsZeroAndNotifies() throws Exception {
        Product product = new Product(null, 21, 5, "SEASONAL", "Grapes", null,
                TODAY.plusDays(10), TODAY.plusDays(20));
        Order order = saveOrder(product);

        processOrder(order);

        assertStock(product, 0);
        verify(notificationService).sendOutOfStockNotification("Grapes");
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void replenishmentExactlyOnSeasonEndAnnouncesDelay() throws Exception {
        Product product = new Product(null, 10, 0, "SEASONAL", "Watermelon", null,
                TODAY.minusDays(1), TODAY.plusDays(10));
        Order order = saveOrder(product);

        processOrder(order);

        assertStock(product, 0);
        assertEquals(10, reload(product).getLeadTime());
        verify(notificationService).sendDelayNotification(10, "Watermelon");
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void expiryTodayClearsStockAndNotifies() throws Exception {
        Product product = new Product(null, 15, 4, "EXPIRABLE", "Milk", TODAY, null, null);
        Order order = saveOrder(product);

        processOrder(order);

        assertStock(product, 0);
        verify(notificationService).sendExpirationNotification("Milk", TODAY);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void zeroStockSendsExpirationNotification() throws Exception {
        Product product = new Product(null, 15, 0, "EXPIRABLE", "Milk", TODAY.plusDays(1), null, null);
        Order order = saveOrder(product);

        processOrder(order);

        assertStock(product, 0);
        verify(notificationService).sendExpirationNotification("Milk", TODAY.plusDays(1));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void notificationFailureRollsBackPreviouslySavedStock() {
        Product cable = new Product(null, 15, 3, "NORMAL", "USB Cable", null, null, null);
        Product expiredMilk = new Product(null, 15, 4, "EXPIRABLE", "Milk", TODAY.minusDays(1), null, null);
        Order order = saveOrder(cable, expiredMilk);
        doThrow(new IllegalStateException("Notification unavailable"))
                .when(notificationService).sendExpirationNotification("Milk", TODAY.minusDays(1));

        assertThrows(NestedServletException.class,
                () -> mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId())));

        assertStock(cable, 3);
        assertStock(expiredMilk, 4);
        verify(notificationService).sendExpirationNotification("Milk", TODAY.minusDays(1));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void emptyOrderReturnsIdWithoutNotifications() throws Exception {
        Order order = saveOrder();

        processOrder(order);

        assertEquals(0, productRepository.count());
        verifyNoInteractions(notificationService);
    }

    @Test
    void repeatedRequestConsumesAnotherUnit() throws Exception {
        Product product = new Product(null, 15, 3, "NORMAL", "USB Cable", null, null, null);
        Order order = saveOrder(product);

        processOrder(order);
        assertStock(product, 2);
        processOrder(order);

        assertStock(product, 1);
        verifyNoInteractions(notificationService);
    }

    @Test
    void unknownTypeKeepsStockUnchanged() throws Exception {
        Product product = new Product(null, 15, 3, "UNKNOWN", "Unknown item", null, null, null);
        Order order = saveOrder(product);

        processOrder(order);

        assertStock(product, 3);
        verifyNoInteractions(notificationService);
    }

    @Test
    void missingOrderReturnsNotFound() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/processOrder", Long.MAX_VALUE))
                .andExpect(status().isNotFound());

        verifyNoInteractions(notificationService);
    }

    @Test
    void nonnumericOrderIdIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/orders/not-a-number/processOrder"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(notificationService);
    }

    private Order saveOrder(Product... products) {
        productRepository.saveAll(Arrays.asList(products));
        return orderRepository.save(new Order(null, new LinkedHashSet<>(Arrays.asList(products))));
    }

    private void processOrder(Order order) throws Exception {
        mockMvc.perform(post("/orders/{orderId}/processOrder", order.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"id\":" + order.getId() + "}", true));
    }

    private void assertStock(Product product, int expected) {
        assertEquals(expected, reload(product).getAvailable(), product.getName());
    }

    private Product reload(Product product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock testClock() {
            return FIXED_CLOCK;
        }
    }
}
