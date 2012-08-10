package wrudp;

import java.net.InetSocketAddress;
import java.lang.reflect.*;

class INSACache<E extends INSAEntry> {
	protected int        ld_sz;
	protected INSAEntry  []cache;
	protected Class<E>     cls;

	INSACache(int ld_sz, Class<E> cls)
	{
		int i;

		this.ld_sz = ld_sz;
		this.cls   = cls;

		cache = new INSAEntry[1<<ld_sz];
		for ( i=0; i<cache.length; i++ )
			cache[i] = null;
	}

	protected int hash(int addr, int port)
	{
		int m = -1640531535; /* == 2654435761 */
		int h = ((addr * m) >> (32-16)) + (port << 16) ;
		h = (h * m) >> (32 - ld_sz);
		return (h & ((1<<ld_sz) - 1));
	}

	public synchronized INSAEntry find(int addr, int port)
	{
		int       h = hash(addr,port);
		INSAEntry e = cache[h];

		return null != e && e.equals(addr, port) ? e : null;
	}

	protected INSAEntry mkEntry(int addr, int port)
	{
		try {
			Class<?> [] parm_types = { int.class, int.class };
			Constructor<E> ct      = cls.getConstructor(parm_types);
			Object   [] args       = { addr, port           };
			return ct.newInstance( args );
		} catch (Throwable e) {
			System.err.println("internal error - unable to create INSAEntry");
			e.printStackTrace();
			System.exit(1);
		}

		/* can never get here */
		return null;
	}

	protected INSAEntry findAdd(int hash, int addr, int port)
	{
	int       h = hash(addr,port);
	INSAEntry e = cache[h];

		if ( null == e ) {
			e = cache[h] = mkEntry(addr, port);	
		} else {
			if ( ! e.equals(addr, port) )
				e = null;
		}
		return e;
	}

	public synchronized INSAEntry findAdd(int addr, int port)
	{
	int       h = hash(addr,port);
	INSAEntry e = cache[h];

		if ( null == e ) {
			e = cache[h] = mkEntry(addr, port);	
		} else {
			if ( ! e.equals(addr,port) )
				e = null;
		}
		return e;
	}

	public synchronized INSAEntry getReplace(int addr, int port)
	{
		int h = hash(addr,port);

		if ( null == cache[h] ) {
			return (cache[h] = mkEntry(addr, port));
		}
		return cache[h].update(addr, port);
	}
}
