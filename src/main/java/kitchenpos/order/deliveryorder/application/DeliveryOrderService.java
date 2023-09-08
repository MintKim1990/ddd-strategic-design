package kitchenpos.order.deliveryorder.application;

import kitchenpos.exhibit.menu.domain.Menu;
import kitchenpos.exhibit.menu.domain.MenuRepository;
import kitchenpos.order.deliveryorder.domain.DeliveryOrder;
import kitchenpos.order.deliveryorder.domain.DeliveryOrderRepository;
import kitchenpos.order.deliveryorder.infra.KitchenridersClient;
import kitchenpos.order.eatinorder.domain.OrderTableRepository;
import kitchenpos.order.shared.domain.OrderLineItem;
import kitchenpos.order.shared.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static kitchenpos.order.shared.domain.OrderStatus.*;

@Service
public class DeliveryOrderService {
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final MenuRepository menuRepository;
    private final OrderTableRepository orderTableRepository;
    private final KitchenridersClient kitchenridersClient;

    public DeliveryOrderService(
            final DeliveryOrderRepository deliveryOrderRepository,
            final MenuRepository menuRepository,
            final OrderTableRepository orderTableRepository,
            final KitchenridersClient kitchenridersClient
    ) {
        this.deliveryOrderRepository = deliveryOrderRepository;
        this.menuRepository = menuRepository;
        this.orderTableRepository = orderTableRepository;
        this.kitchenridersClient = kitchenridersClient;
    }

    @Transactional
    public DeliveryOrder create(final DeliveryOrder request) {
        final List<OrderLineItem> orderLineItemRequests = request.getOrderLineItems();
        if (Objects.isNull(orderLineItemRequests) || orderLineItemRequests.isEmpty()) {
            throw new IllegalArgumentException();
        }
        final List<Menu> menus = menuRepository.findAllByIdIn(
                orderLineItemRequests.stream()
                        .map(OrderLineItem::getMenuId)
                        .collect(Collectors.toList())
        );
        if (menus.size() != orderLineItemRequests.size()) {
            throw new IllegalArgumentException();
        }
        final List<OrderLineItem> orderLineItems = new ArrayList<>();
        for (final OrderLineItem orderLineItemRequest : orderLineItemRequests) {
            final long quantity = orderLineItemRequest.getQuantity();
            if (quantity < 0) {
                throw new IllegalArgumentException();
            }
            final Menu menu = menuRepository.findById(orderLineItemRequest.getMenuId())
                    .orElseThrow(NoSuchElementException::new);
            if (!menu.isDisplayed()) {
                throw new IllegalStateException();
            }
            if (menu.getPrice().compareTo(orderLineItemRequest.getPrice()) != 0) {
                throw new IllegalArgumentException();
            }
            final OrderLineItem orderLineItem = new OrderLineItem();
            orderLineItem.setMenu(menu);
            orderLineItem.setQuantity(quantity);
            orderLineItems.add(orderLineItem);
        }
        DeliveryOrder order = new DeliveryOrder();
        order.setId(UUID.randomUUID());
        order.setStatus(WAITING);
        order.setOrderDateTime(LocalDateTime.now());
        order.setOrderLineItems(orderLineItems);
        final String deliveryAddress = request.getDeliveryAddress();
        if (Objects.isNull(deliveryAddress) || deliveryAddress.isEmpty()) {
            throw new IllegalArgumentException();
        }
        order.setDeliveryAddress(deliveryAddress);
        return deliveryOrderRepository.save(order);
    }

    @Transactional
    public DeliveryOrder accept(final UUID orderId) {
        final DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(NoSuchElementException::new);
        if (order.getStatus() != WAITING) {
            throw new IllegalStateException();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final OrderLineItem orderLineItem : order.getOrderLineItems()) {
            sum = orderLineItem.getMenu()
                    .getPrice()
                    .multiply(BigDecimal.valueOf(orderLineItem.getQuantity()));
        }
        kitchenridersClient.requestDelivery(orderId, sum, order.getDeliveryAddress());
        order.setStatus(ACCEPTED);
        return order;
    }

    @Transactional
    public DeliveryOrder serve(final UUID orderId) {
        final DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(NoSuchElementException::new);
        if (order.getStatus() != ACCEPTED) {
            throw new IllegalStateException();
        }
        order.setStatus(SERVED);
        return order;
    }

    @Transactional
    public DeliveryOrder startDelivery(final UUID orderId) {
        final DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(NoSuchElementException::new);
        if (order.getStatus() != SERVED) {
            throw new IllegalStateException();
        }
        order.setStatus(DELIVERING);
        return order;
    }

    @Transactional
    public DeliveryOrder completeDelivery(final UUID orderId) {
        final DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(NoSuchElementException::new);
        if (order.getStatus() != DELIVERING) {
            throw new IllegalStateException();
        }
        order.setStatus(DELIVERED);
        return order;
    }

    @Transactional
    public DeliveryOrder complete(final UUID orderId) {
        final DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(NoSuchElementException::new);
        final OrderStatus status = order.getStatus();
        if (status != DELIVERED) {
            throw new IllegalStateException();
        }
        order.setStatus(COMPLETED);
        return order;
    }

    @Transactional(readOnly = true)
    public List<DeliveryOrder> findAll() {
        return deliveryOrderRepository.findAll();
    }
}
