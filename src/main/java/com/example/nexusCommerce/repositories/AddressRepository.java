package com.example.nexusCommerce.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.nexusCommerce.schema.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByCreatedAtAsc(Long userId);

    List<Address> findByUserIdAndDefaultShippingTrue(Long userId);

    List<Address> findByUserIdAndDefaultBillingTrue(Long userId);
}
