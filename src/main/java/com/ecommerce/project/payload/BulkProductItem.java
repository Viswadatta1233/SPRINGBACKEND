package com.ecommerce.project.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry in a bulk product-creation request: which category the product belongs to,
 * plus the product details themselves.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkProductItem {
    private Long categoryId;
    private ProductDTO product;
}
