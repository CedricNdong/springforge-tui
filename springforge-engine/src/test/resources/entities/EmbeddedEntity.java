package com.example.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Embedded;

@Entity
public class EmbeddedEntity {

    @Id
    private Long id;

    @Embedded
    private Address address;

    private String name;
}
