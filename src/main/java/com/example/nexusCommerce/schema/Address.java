package com.example.nexusCommerce.schema;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "addresses", indexes = {
    @Index(name = "idx_addr_user", columnList = "user_id"),
    @Index(name = "idx_addr_user_default", columnList = "user_id,is_default_shipping"),
})
@SQLDelete(sql = "UPDATE addresses SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class Address extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false, nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String label = "Home";

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(name = "line1", nullable = false)
    private String line1;

    @Column(name = "line2")
    private String line2;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 2)
    @Builder.Default
    private String country = "IN";

    @Column(name = "is_default_shipping", nullable = false)
    @Builder.Default
    private Boolean defaultShipping = Boolean.FALSE;

    @Column(name = "is_default_billing", nullable = false)
    @Builder.Default
    private Boolean defaultBilling = Boolean.FALSE;
}
