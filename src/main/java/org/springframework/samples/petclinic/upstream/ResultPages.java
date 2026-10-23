package org.springframework.samples.petclinic.upstream;

import java.util.List;

/**
 * Cuts a list into the pages of the GraphQL schema. Every page type has the same shape as
 * <code>OwnerPage</code>: the items, the page number, the number of pages and the number
 * of items across all pages.
 */
public final class ResultPages {

	/**
	 * Largest page a client may ask for, the same as for owners and pets.
	 */
	public static final int MAX_PAGE_SIZE = 100;

	private ResultPages() {
	}

	/**
	 * Creates one page type of the schema. A record whose components are the items, the
	 * page, the number of pages and the number of items fits, so its constructor can be
	 * passed as a method reference.
	 *
	 * @param <T> the type of the items
	 * @param <P> the page type
	 */
	@FunctionalInterface
	public interface Factory<T, P> {

		P create(List<T> items, int page, int totalPages, int totalItems);

	}

	/**
	 * Returns one page of the items. Pages count from 0, and a page number below 0
	 * returns the first page. A size outside 1 to {@value #MAX_PAGE_SIZE} is moved to the
	 * nearest limit. A page past the last one holds an empty list.
	 */
	public static <T, P> P page(List<T> items, int page, int size, Factory<T, P> factory) {
		int pageNumber = pageNumber(page);
		int pageSize = pageSize(size);
		int from = (int) Math.min((long) pageNumber * pageSize, items.size());
		int to = Math.min(from + pageSize, items.size());
		int totalPages = (items.size() + pageSize - 1) / pageSize;
		return factory.create(List.copyOf(items.subList(from, to)), pageNumber, totalPages, items.size());
	}

	/**
	 * The page number a client asked for, counted from 0, moved to 0 when it is lower.
	 */
	public static int pageNumber(int page) {
		return Math.max(page, 0);
	}

	/**
	 * The page size a client asked for, moved into the range from 1 to
	 * {@value #MAX_PAGE_SIZE}.
	 */
	public static int pageSize(int size) {
		return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
	}

}
