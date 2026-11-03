package com.example.mcpserver.service;

import com.example.mcpserver.dto.tool.ProductDetails;
import com.example.mcpserver.dto.tool.ProductSearchResult;
import com.example.mcpserver.dto.tool.SearchFacets;
import com.example.mcpserver.model.ProductSearchParams;

/** Read-only product operations offered to the AI. */
public interface ProductCatalogService {

    ProductSearchResult search(ProductSearchParams params);

    ProductDetails getProduct(String id);

    SearchFacets getFacets();
}
