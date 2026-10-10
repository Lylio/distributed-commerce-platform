package com.lylecommerce.order.domain;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record Product(UUID id, String name, String subtitle, String category, String description,
                      List<String> features, String illustration, String color, BigDecimal unitPrice, String currency) {}
