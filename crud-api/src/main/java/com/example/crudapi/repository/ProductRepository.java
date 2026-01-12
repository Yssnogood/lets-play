package com.example.crudapi.repository;

import com.example.crudapi.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByUserId(String userId);

    // Delete all products belonging to a user
    void deleteByUserId(String userId);
}
