package com.pisey.elearning.entity;

/** A child with a position among its siblings ({@code sort_order}, unique per parent, deferred). */
public interface Ordered {

    Integer getSortOrder();

    void setSortOrder(Integer sortOrder);
}
