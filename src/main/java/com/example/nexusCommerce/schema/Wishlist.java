package com.example.nexusCommerce.schema;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
@Table(name = "wishlists", indexes = {
    @Index(name = "idx_wl_user", columnList = "user_id"),
})
@SQLDelete(sql = "UPDATE wishlists SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class Wishlist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false, nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    @Builder.Default
    private String name = "Wishlist";

    @Builder.Default
    @OneToMany(mappedBy = "wishlist", fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<WishlistItem> items = new ArrayList<>();
}
