package wrudp;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;

class INSAEntry {
	protected int                     addr, port;
	protected InetSocketAddress       sa;
	protected byte                    []buf;

	public boolean equals(INSAEntry e)
	{
		return this.addr == e.addr && this.port == e.port;
	}

	public boolean equals(int addr, int port)
	{
		return this.addr == addr && this.port == port;
	}


	public void set(int addr_in, int port_in)
	{
		addr = addr_in; port = port_in;
		sa   = new InetSocketAddress( int2inet(addr, buf), port );
	}

	public INSAEntry update( int addr_in, int port_in )
	{
		if ( ! equals(addr_in, port_in) ) {
			set(addr_in, port_in);
		}
		return this;
	}

	public InetSocketAddress get_insa()
	{
		return sa;
	}

	public int get_addr()
	{
		return addr;
	}

	public int get_port()
	{
		return port;
	}

	public INSAEntry(int addr, int port)
	{
		buf = new byte[4];
		set(addr, port);
	}

	protected static InetAddress int2inet(int ia, byte[]ba)
	{
        ba[0] = (byte)((ia>>24) & 0xff);
        ba[1] = (byte)((ia>>16) & 0xff);
        ba[2] = (byte)((ia>> 8) & 0xff);
        ba[3] = (byte)((ia>> 0) & 0xff);
        try {
            return InetAddress.getByAddress(ba);
        } catch (UnknownHostException e) {
        }
		/* should never get here */
        return null;
	}
}
