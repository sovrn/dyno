package com.netflix.dyno.connectionpool.impl.hash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class DynoBinarySearch<T extends Comparable<T>> {
	
	private final List<DynoTokenRange<T>> rangeList = new ArrayList<DynoTokenRange<T>>();
	
	public DynoBinarySearch(List<T> list) {

		if (list.isEmpty()) {
			throw new RuntimeException("List must not be empty");
		}

		if (list.size() == 1) {
			rangeList.add(new DynoTokenRange<T>(null, list.get(0)));
			
		} else {
			// add the first range
			rangeList.add(new DynoTokenRange<T>(null, list.get(0)));
			// add rest of the tokens
			for (int i=1; i<(list.size()); i++) {
				rangeList.add(new DynoTokenRange<T>(list.get(i-1), list.get(i)));
			}
			
			rangeList.get(rangeList.size()-1).isLastRange = true;
		}
	}


	public T getTokenOwner(T token) {
		
		// Some quick boundary checks
		
		if (rangeList.size() == 1) {
			return rangeList.get(0).getTokenOwner();
		}
		
		DynoTokenRange<T> firstRange = rangeList.get(0);
		DynoTokenRange<T> lastRange = rangeList.get(rangeList.size()-1);
		
		if (firstRange.compareTo(token) == 0) {
			// Token is smaller than FIRST range, map to first range. 
			return firstRange.getTokenOwner();
		}
		
		if (lastRange.compareTo(token) < 0) {
			// Token is greater than LAST range, map to first range. 
			return firstRange.getTokenOwner();
		}

		int index = Collections.binarySearch(rangeList, token);
		
		if (index < 0) {
			throw new RuntimeException("Token not found!: " + token);
		}
		
		return rangeList.get(index).getTokenOwner();
	}
	
	public String toString() {
		
		StringBuilder sb = new StringBuilder("[DynoBinarySearch:\n");
		for (DynoTokenRange<T> r : rangeList) {
			sb.append(r.toString()).append("\n");
		}
		sb.append("]");
		return sb.toString();
	}
	
	static class DynoTokenRange<T extends Comparable<T>> implements Comparable<T> {
		
		final T start; 
		final T end;
		boolean isFirstRange = false;
		boolean isLastRange = false;
		
		DynoTokenRange(T s, T e) {
			this.start = s;
			this.end = e;
			
			if (s == null) {
				isFirstRange = true;
			}
			
			if (isFirstRange) {
				if (end == null) {
					throw new RuntimeException("Bad Range: end must not be null");
				}
			} else if (!(lessThan(start, end))) {
				throw new RuntimeException("Bad Range: start must be less than end: " + this.toString());
			}
		}
		
		public T getTokenOwner() {
			return end;
		}
		
		public String toString() {
			if (isFirstRange) {
				return "(null," + end + "]";
			} else {
				return "(" + start + "," + end + "]";
			}
		}

		@Override
		public int compareTo(T key) {

			//System.out.println("Key: " + key + ", start: " + start + ", end: " + end);
			
			// Boundary checks, to be safe!
			
			if (isFirstRange) {
				
				if (lessThanEquals(key, end)) {
					return 0;  // This key is within this range
				} else {
					// else This range is smaller than this key
					return -1;
				}
			}
			
			// Functionality for any other range i.e in another position in the list except for the first 
			if (lessThanEquals(key, start)) {
				// This range is greater than the key
				return 1;
			}
			
			if (lessThan(start, key) && lessThanEquals(key, end)) {
				// This key is within this range
				return 0;  
			}
			
			if (lessThan(end, key)) {
				// This range is smaller than this key
				return -1;
			} else {
				throw new RuntimeException("Invalid key for bin search: " + key + ", this range: " + this.toString());
			}
		}
		
		private boolean lessThan(T left, T right) {
			return left.compareTo(right) < 0;
		}

		private boolean lessThanEquals(T left, T right) {
			return left.compareTo(right) <= 0;
		}
	}
	
	public static class UnitTest {
		
		@Test
		public void testTokenRange() throws Exception {

			DynoTokenRange<Integer> r1 = new DynoTokenRange<Integer>(20, 40);
			
			Assert.assertEquals(1, r1.compareTo(10));
			Assert.assertEquals(1, r1.compareTo(15));
			Assert.assertEquals(1, r1.compareTo(20));
			Assert.assertEquals(0, r1.compareTo(22));
			Assert.assertEquals(0, r1.compareTo(30));
			Assert.assertEquals(0, r1.compareTo(40));
			Assert.assertEquals(-1, r1.compareTo(42));

			 
			// First Range
			r1 = new DynoTokenRange<Integer>(null, 40);
			
			Assert.assertEquals(0, r1.compareTo(10));
			Assert.assertEquals(0, r1.compareTo(15));
			Assert.assertEquals(0, r1.compareTo(20));
			Assert.assertEquals(0, r1.compareTo(22));
			Assert.assertEquals(0, r1.compareTo(30));
			Assert.assertEquals(0, r1.compareTo(40));
			Assert.assertEquals(-1, r1.compareTo(42));
		}
		
		@Test
		public void testTokenSearch() throws Exception {
			
			List<Integer> list = Arrays.asList(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
			
			DynoBinarySearch<Integer> search = new DynoBinarySearch<Integer>(list);
			
			for (int i=0; i<=133; i++) {
				
				Integer token = search.getTokenOwner(i);
				Integer expected = getExpectedToken(i);
				
				Assert.assertEquals(expected, token);
			}
		}
		

		@Test
		public void testTokenDistribution() throws Exception {
			
			List<Long> tokens = Arrays.asList(611697721L, 2043353485L, 3475009249L);
			
			DynoBinarySearch<Long> search = new DynoBinarySearch<Long>(tokens);
			
			Map<Long, Long> tokenCount = new HashMap<Long, Long>();
			
			tokenCount.put(611697721L, 0L);
			tokenCount.put(2043353485L, 0L);
			tokenCount.put(3475009249L, 0L);

			Murmur1HashPartitioner hash = new Murmur1HashPartitioner();
			
			for (int i=0; i<1000000; i++) {
				
				// Compute the hash
				long lHash = hash.hash("" + i);
				
				// Now lookup the node
				Long token = search.getTokenOwner(lHash);
				
				Long count = tokenCount.get(token);
				count++;
				tokenCount.put(token, count);
			}
			
			long total = 0;
			for (Long value : tokenCount.values()) {
				total += value;
			}
			
			for (Long token : tokenCount.keySet()) {
				
				Long value = tokenCount.get(token);
				Long percent = value*100/total;
				
				Assert.assertTrue("Percentage is off for tokenCount: " + tokenCount, percent >= 30 && percent <= 35);
			}
		}
		
		private int getExpectedToken(int key) throws Exception {
			
			if (key < 10) {
				return 10;
			}
			
			if (key > 100) {
				return 10;
			}
			
			if (key % 10 == 0) {
				return key;
			}
			
			return key + (10 - key%10);
		}
	}
	
}
