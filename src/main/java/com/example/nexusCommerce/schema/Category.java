package com.example.nexusCommerce.schema;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name="categories")
@SQLDelete(sql = "UPDATE categories SET deleted_datetime = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_datetime is NULL")
public class Category extends BaseEntity{
    
    @Column(nullable=false)
    private String name;
}
