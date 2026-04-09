package com.example.nexusCommerce.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.nexusCommerce.adapters.OrderAdapter;
import com.example.nexusCommerce.dtos.GetOrderResponseDto;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.OrderRepository;
import com.example.nexusCommerce.schema.Order;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderAdapter orderAdapter;
    
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

}
