package com.example.nexusCommerce.schema;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
@Table(name = "categories")
@SQLDelete(sql = "UPDATE categories SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class Category extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /**
     * Null for top-level categories. Two subcategories under different parents are allowed
     * to share a name (Men/Denim vs Women/Denim); V15 replaces V8's flat unique-name
     * constraint with parent-scoped uniqueness.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "parent_id", insertable = false, updatable = false)
    private Long parentId;

    @Column(length = 500)
    private String description;

    @Column(length = 80)
    private String slug;

    @Builder.Default
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<Category> children = new ArrayList<>();
}
