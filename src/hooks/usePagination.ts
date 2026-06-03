import { useMemo } from 'react';

export type VisiblePageItem = number | '...';

interface GetVisiblePagesWindowOptions {
  totalPages: number;
  currentPage: number;
  maxVisible?: number;
  withDots?: false;
}

interface GetVisiblePagesDotsOptions {
  totalPages: number;
  currentPage: number;
  delta?: number;
  withDots: true;
}

export function getVisiblePages(options: GetVisiblePagesWindowOptions): number[];
export function getVisiblePages(options: GetVisiblePagesDotsOptions): VisiblePageItem[];
export function getVisiblePages(
  options: GetVisiblePagesWindowOptions | GetVisiblePagesDotsOptions
): VisiblePageItem[] {
  const { totalPages, currentPage } = options;
  if (totalPages <= 0) {
    return [];
  }

  if ('withDots' in options && options.withDots) {
    const delta = options.delta ?? 2;
    const range: number[] = [];
    const rangeWithDots: VisiblePageItem[] = [];
    let lastPage: number | undefined;

    for (let page = 1; page <= totalPages; page += 1) {
      if (
        page === 1 ||
        page === totalPages ||
        (page >= currentPage - delta && page <= currentPage + delta)
      ) {
        range.push(page);
      }
    }

    range.forEach((page) => {
      if (lastPage !== undefined) {
        if (page - lastPage === 2) {
          rangeWithDots.push(lastPage + 1);
        } else if (page - lastPage !== 1) {
          rangeWithDots.push('...');
        }
      }
      rangeWithDots.push(page);
      lastPage = page;
    });

    return rangeWithDots;
  }

  const maxVisible = options.maxVisible ?? 10;
  if (totalPages <= maxVisible) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  const halfVisible = Math.floor(maxVisible / 2);
  let startPage = Math.max(1, currentPage - halfVisible);
  const endPage = Math.min(totalPages, startPage + maxVisible - 1);

  if (endPage - startPage < maxVisible - 1) {
    startPage = Math.max(1, endPage - maxVisible + 1);
  }

  return Array.from(
    { length: endPage - startPage + 1 },
    (_, index) => startPage + index
  );
}

interface UsePaginationOptions<T> {
  items: T[];
  currentPage: number;
  itemsPerPage: number;
}

export function usePagination<T>({
  items,
  currentPage,
  itemsPerPage,
}: UsePaginationOptions<T>) {
  const totalPages = Math.ceil(items.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;

  const paginatedItems = useMemo(
    () => items.slice(startIndex, endIndex),
    [items, startIndex, endIndex]
  );

  const visiblePages = useMemo(
    () =>
      getVisiblePages({
        totalPages,
        currentPage,
      }) as number[],
    [totalPages, currentPage]
  );

  return {
    totalPages,
    startIndex,
    endIndex,
    paginatedItems,
    visiblePages,
  };
}
