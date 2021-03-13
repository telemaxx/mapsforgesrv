package com.telemaxx.mapsforgesrv;
import java.util.HashSet;
import java.util.Set;

import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.queue.Job;
import org.mapsforge.map.model.common.Observer;

public class DummyCache implements TileCache {

	HashSet<Integer> set = new HashSet<>(10000);

	@Override
	public void put(Job job, TileBitmap tile) {
		set.add(job.hashCode());
	}

	@Override
	public boolean containsKey(Job job) {
		return set.contains(job.hashCode());
	}

	@Override
	public void destroy() {
	}

	@Override
	public TileBitmap get(Job job) {
		throw new java.lang.UnsupportedOperationException();
	}

	@Override
	public int getCapacity() {
		throw new java.lang.UnsupportedOperationException();
	}

	@Override
	public int getCapacityFirstLevel() {
		throw new java.lang.UnsupportedOperationException();
	}

	@Override
	public TileBitmap getImmediately(Job job) {
		throw new java.lang.UnsupportedOperationException();
	}

	@Override
	public void setWorkingSet(Set<Job> jobs) {
		throw new java.lang.UnsupportedOperationException();
	}

	@Override
	public void purge() {
		set.clear();
	}

	@Override
	public void addObserver(Observer observer) {
		throw new java.lang.UnsupportedOperationException();
	}

	@Override
	public void removeObserver(Observer observer) {
		throw new java.lang.UnsupportedOperationException();
	}

}
