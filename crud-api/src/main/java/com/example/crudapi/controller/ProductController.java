package com.example.crudapi.controller;

import com.example.crudapi.model.Product;
import com.example.crudapi.repository.ProductRepository;
import com.example.crudapi.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductRepository productRepository;
	private final JwtUtil jwtUtil;

	public ProductController(ProductRepository productRepository, JwtUtil jwtUtil) {
		this.productRepository = productRepository;
		this.jwtUtil = jwtUtil;
	}

	// CREATE - Post Request - need authentication
	@PostMapping
	public ResponseEntity<Product> createProduct(@RequestBody Product product,
							 @RequestHeader("Authorization") String authHeader) {
		String token = authHeader.replace("Bearer ", "");
		String userId = jwtUtil.extractUserId(token);

		product.setUserId(userId);
		Product saved = productRepository.save(product);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	// READ ALL - Get all request- isPublic
	@GetMapping
	public List<Product> getProducts() {
		return productRepository.findAll();
	}

	// UPDATE - Put request - allow owner or admin to update a product
	@PutMapping("/{id}")
	public ResponseEntity<?> updateProduct(@PathVariable String id,
						   @RequestBody Product updatedProduct,
						   @RequestHeader("Authorization") String authHeader) {
		String token = authHeader.replace("Bearer ", "");
		String userId = jwtUtil.extractUserId(token);
		String role = jwtUtil.extractRole(token);

		Optional<Product> existingOpt = productRepository.findById(id);
		if (existingOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
		}

		Product existing = existingOpt.get();
		if (!existing.getUserId().equals(userId) && !role.equals("ROLE_ADMIN")) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not allowed");
		}

		existing.setName(updatedProduct.getName());
		existing.setDescription(updatedProduct.getDescription());
		existing.setPrice(updatedProduct.getPrice());

		productRepository.save(existing);
		return ResponseEntity.ok(existing);
	}

	// ✅ DELETE product (only owner or admin)
	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteProduct(@PathVariable String id,
					   @RequestHeader("Authorization") String authHeader) {
		String token = authHeader.replace("Bearer ", "");
		String userId = jwtUtil.extractUserId(token);
		String role = jwtUtil.extractRole(token);

		Optional<Product> existingOpt = productRepository.findById(id);
		if (existingOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
		}

		Product existing = existingOpt.get();
		if (!existing.getUserId().equals(userId) && !role.equals("ROLE_ADMIN")) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not allowed");
		}

		productRepository.delete(existing);
		return ResponseEntity.ok("Product deleted");
	}
}