package com.example.nexusCommerce.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.adapters.OrderAdapter;
import com.example.nexusCommerce.dtos.CreateOrderRequestDto;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.dtos.OrderItemActionDto;
import com.example.nexusCommerce.dtos.updateOrderRequestDto;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.OrderProductsRepository;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Order;
import com.example.nexusCommerce.schema.OrderItemAction;
import com.example.nexusCommerce.schema.OrderProducts;
import com.example.nexusCommerce.schema.OrderStatus;
import com.example.nexusCommerce.schema.Product;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderProductsRepository orderProductsRepository;
    private final OrderAdapter orderAdapter;
    private final ProductRepository productRepository;
    
    public List<GetOrderResponseDto> getAllOrders(){
        List<Order> orders = orderRepository.findAll();
        return orderAdapter.mapToGetOrderResponseDtoList(orders);
    }

    public GetOrderResponseDto getOrderById(Long id){
        Order order = orderRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Order with id: " + id + " not found"));
        return orderAdapter.mapToGetOrderResponseDto(order);
    }

    public void deleteOrder(Long id){
        Order order = orderRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        orderRepository.delete(order);
    }

    @Transactional
    public GetOrderResponseDto createOrder(CreateOrderRequestDto createOrderRequestDto){
        Order order = Order.builder()
                        .status(OrderStatus.PENDING)
                        .build();

        orderRepository.save(order);

        if(createOrderRequestDto.getOrderItems()!= null){
            List<Long> productIds = createOrderRequestDto.getOrderItems().stream()
                                        .map(item -> item.getProductId()).collect(Collectors.toList());
                                        
            List<Product> products = productRepository.findAllById(productIds);

            Map<Long, Product> productMap = products.stream()
                                                .collect(Collectors.toMap(Product::getId, Function.identity()));

            for(Long id: productIds){
                if(!productMap.containsKey(id)){
                    throw new ResourceNotFoundException("Product not found with id: " + id);
                }
            }

            List<OrderProducts> orderProducts = new ArrayList<>();
            for(var itemDto : createOrderRequestDto.getOrderItems()){
                Product product = productMap.get(itemDto.getProductId());
                orderProducts.add(OrderProducts.builder()
                                    .order(order)
                                    .product(product)
                                    .quantity(itemDto.getQuantity()!=null ? itemDto.getQuantity() : 1)
                                    .build());
            }
            orderProductsRepository.saveAll(orderProducts);
        }

        return orderAdapter.mapToGetOrderResponseDto(order);
    }

    public GetOrderResponseDto updateOrder(Long id, updateOrderRequestDto updateOrderRequestDto){
        Order order = orderRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Order not found with id " + id));

        if(updateOrderRequestDto.getStatus()!=null){
            order.setStatus(updateOrderRequestDto.getStatus());
            orderRepository.save(order);
        }
        if(updateOrderRequestDto.getOrderItems()!= null){
            List<Long> productIds = updateOrderRequestDto.getOrderItems().stream()
                                                            .map(item -> item.getProductId())
                                                            .collect(Collectors.toList());
            List<Product> products = productRepository.findAllById(productIds);

            Map<Long, Product> productMap = products.stream()
                                                .collect(Collectors.toMap(Product::getId, Function.identity()));

            for(var pid : productIds){
                if(!productMap.containsKey(pid)){
                    throw new ResourceNotFoundException("Product not found with id: " + pid);
                }
            }

            List<OrderProducts> toSave = new ArrayList<>();
            List<OrderProducts> toDelete = new ArrayList<>();

            Map<Long, OrderProducts> existingItems = orderProductsRepository.findByOrderWithProduct(order).stream()
                                                        .collect(Collectors.toMap(op -> op.getProduct().getId(), Function.identity()));

            for(OrderItemActionDto itemAction : updateOrderRequestDto.getOrderItems()){
                Product product = productMap.get(itemAction.getProductId());

                OrderProducts existing = existingItems.get(product.getId());

                switch(itemAction.getAction()){
                    case ADD -> {
                        if(existing != null){
                            int addQty = (itemAction.getQuantity()!=null ? itemAction.getQuantity() : 1);
                            existing.setQuantity(existing.getQuantity() + addQty);
                            toSave.add(existing);
                        }
                        else{
                            OrderProducts newItem = OrderProducts.builder()
                                                        .order(order)
                                                        .product(product)
                                                        .quantity(itemAction.getQuantity()!= null ? itemAction.getQuantity() : 1)
                                                        .build();
                            existingItems.put(product.getId(), newItem);
                            toSave.add(newItem);
                        }
                    }
                    case REMOVE ->{
                        if(existing == null){
                            throw new ResourceNotFoundException("Product not found with id: " + product.getId());
                        }
                        toDelete.add(existing);
                        existingItems.remove(product.getId());
                    }
                    case INCREMENT ->{
                        if(existing == null){
                            throw new ResourceNotFoundException("Product not found with id: " + product.getId());
                        }
                        existing.setQuantity(existing.getQuantity() +1);
                        toSave.add(existing);
                    }
                    case DECREMENT -> {
                        if(existing == null){
                            throw new ResourceNotFoundException("Product not found with id: " + product.getId());
                        }
                        if(existing.getQuantity() <= 1){
                            toDelete.add(existing);
                            existingItems.remove(product.getId());
                        }
                        else{
                            existing.setQuantity(existing.getQuantity() - 1);
                            toSave.add(existing);
                        }
                    }
                }
            }
            
            if(!toSave.isEmpty()){
                orderProductsRepository.saveAll(toSave);
            }
            if(!toDelete.isEmpty()){
                orderProductsRepository.deleteAll(toDelete);
            }
        }

        return orderAdapter.mapToGetOrderResponseDto(order);
    }

}
