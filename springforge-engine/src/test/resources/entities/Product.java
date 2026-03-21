package com.example.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Column;
import javax.persistence.ManyToOne;
import java.math.BigDecimal;

@Entity
public class Product {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private BigDecimal price;

    @ManyToOne
    private Category category;
}
