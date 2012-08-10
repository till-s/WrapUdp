package wrudp;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.lang.UnsupportedOperationException;

class ArrayIterator<T> implements Iterator {
	private T []      arr;
	private int       pos = 0;

	public ArrayIterator(T []array) {
			arr=array;
	}

	public boolean hasNext() 
	{
		return pos < arr.length;
	}

	public T next() throws NoSuchElementException
	{
		if ( hasNext() ) {
			return arr[pos++];
		} else {
			throw new NoSuchElementException();
		}
	}

	public void remove()
	{
		throw new UnsupportedOperationException();
	}

	public int getpos()
	{
		return pos;
	}
}
