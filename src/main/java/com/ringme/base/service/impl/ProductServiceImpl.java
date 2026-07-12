package com.ringme.base.service.impl;

import com.ringme.base.dto.app.request.ProductRequest;
import com.ringme.base.dto.app.response.ProductResponse;
import com.ringme.base.entity.Product;
import com.ringme.base.enums.AppCode;
import com.ringme.base.exception.BusinessLogicException;
import com.ringme.base.repository.ProductRepository;
import com.ringme.base.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public Page<ProductResponse> list(String name, Pageable pageable) {
        Page<Product> page = (name == null || name.isBlank())
                ? productRepository.findAll(pageable)
                : productRepository.findByNameContainingIgnoreCase(name, pageable);
        return page.map(this::toResponse);
    }

    @Override
    public ProductResponse getById(Long id) {
        return toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findEntity(id);
        applyRequest(product, request);
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        productRepository.delete(findEntity(id));
    }

    private Product findEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessLogicException(AppCode.CODE_404, "Product not found: " + id));
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(request.getCategory());
        product.setTags(request.getTags() == null ? new HashSet<>() : new HashSet<>(request.getTags()));
        product.setDescription(request.getDescription());
        product.setStatus(request.getStatus());
        product.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        product.setReleaseDate(request.getReleaseDate());
        product.setPublishedAt(request.getPublishedAt());
    }

    private ProductResponse toResponse(Product product) {
        List<String> tags = new ArrayList<>(product.getTags());
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .category(product.getCategory())
                .tags(tags)
                .description(product.getDescription())
                .status(product.getStatus())
                .featured(product.getFeatured())
                .releaseDate(product.getReleaseDate())
                .publishedAt(product.getPublishedAt())
                .build();
    }
}
