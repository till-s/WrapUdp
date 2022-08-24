package wrudp;

import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.ListIterator;

class UdpProxy implements Runnable {
	protected DatagramChannel    udpChnl;
	protected ByteBuffer         buf;
	protected WrapUdp            hdr;
	protected int                addr, port;

	protected static INSACache<INSAEntry> insas       = new INSACache<INSAEntry>(5, INSAEntry.class);
	protected static UdpProxy             outsideSock = null;
	protected static OStream              ostrm       = null;
	protected static int                  debug       = 0;
	protected static int                  avl_proxies = 4;

	protected static boolean       recycle_local_port = false;

	protected static LinkedList<UdpProxy> lru = new LinkedList<UdpProxy>();

	public int get_addr()
	{
		return addr;
	}

	public int get_port()
	{
		return port;
	}

	public static int debug()
	{
		return debug;
	}

	public static UdpProxy get(int addr, int port)
	{
	UdpProxy p;

		synchronized ( lru ) {
			ListIterator it = lru.listIterator();

			while ( it.hasNext() ) {
				p = (UdpProxy)it.next();	
				if ( p.addr == addr && p.port == port ) {
					/* FOUND; bring to the front of the list unless it is alreay there */
					if ( 0 != it.previousIndex() ) {
						it.remove();
						lru.add( p );
					}
					return p;
				}
			}

			if ( avl_proxies > 0 ) {
				p = new UdpProxy( addr, port );
				avl_proxies--;
			} else {
				/* What to do here? We recycle the least used proxy 
				 * but the UDP server we're talking to may or may not like
				 * that a different client is now using the same local
				 * port from the proxy to connect.
				 * We have two options: we could let the lru proxy die
				 * and allocate a new one (bound to a different port) or
				 * we could reuse the existing port representing a different
				 * client...
				 *
				 * E.g., 'nc' in udp listen mode only accepts traffic
				 * from the first UDP port it receives packets. In this
				 * case, setting 'recycle_local_port' is probably desirable
				 * (but the number of proxies should then be limited to one) 
				 * 
				 */
				p = (UdpProxy)lru.removeLast();

				if ( ! recycle_local_port ) {
					p = null;
					p = new UdpProxy( addr, port );
				} else {
					p.addr = addr; p.port = port;
				}
			}
			lru.add( p );
		}
		return p;
	}

	public static void send( WrapUdp hdr, ByteBuffer pld )
		throws IOException
	{
	INSAEntry           e;
	UdpProxy            p;
	InetSocketAddress dst;

		if ( null == (p = outsideSock) ) {
			/* inside */
			p = get(hdr.get_saddr(), hdr.get_sport());
		}

		synchronized ( insas ) {
			dst = insas.getReplace( hdr.get_daddr(), hdr.get_dport() ).get_insa();
		}

		p.udpChnl.send( pld, dst );
	}


	public UdpProxy(int daddr, int dport)
	{
		this(daddr, dport, null);
	}

	protected UdpProxy(int daddr, int dport, InetSocketAddress my_addr)
	{
		this.addr = daddr; this.port = dport;
		try {
			udpChnl = DatagramChannel.open();
			try {
				udpChnl.socket().bind( my_addr );
			} catch (IOException e) {
				System.err.println("Unable to bind to " + my_addr);
				throw e;
			}
			udpChnl.socket().setReuseAddress( true );
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		buf     = ByteBuffer.allocate( WrapUdp.WRAP_UDP_PLDLN_MAX );
		hdr     = new WrapUdp();
		(new Thread( this )).start();
	}

	protected static int inet2int(InetAddress a)
	{
	byte [] ba   = a.getAddress();
	int     rval =   ((((int)ba[0]) & 0xff) << 24)
	               | ((((int)ba[1]) & 0xff) << 16)
	               | ((((int)ba[2]) & 0xff) <<  8)
	               | ((((int)ba[3]) & 0xff) <<  0);
		return rval;
	}

	protected void
	handleUdp()
		throws IOException, OStream.IncompleteBufferWrittenException
	{
	InetSocketAddress udp_src;

		if ( debug() > 0 ) {
			System.err.println("Waiting for UDP");
		}

		buf.clear();
		udp_src = (InetSocketAddress)udpChnl.receive( buf );
		buf.flip();

		if ( debug() > 0 ) {
			System.err.println("Got datagram from "+udp_src);
		}

		hdr.fill( buf.limit(), inet2int( udp_src.getAddress() ), get_addr(), udp_src.getPort(), get_port() );

		synchronized ( ostrm ) {
			if ( debug() > 0 ) {
				System.err.println("Writing header");
				hdr.dump();
			}
			hdr.write( ostrm );
			ostrm.write( buf );
			ostrm.flush();
		}
	}

	public void run()
	{
		try {
			while ( true ) {
				handleUdp();
			}
		} catch (Throwable e) {
			e.printStackTrace();	
		} finally {
			System.exit(1);
		}
	}

	static int toInt(String s)
	{
		try {
			return Integer.decode(s).intValue();
		} catch ( java.lang.NumberFormatException e ) {
			System.err.println("Unable to convert '" + s + "' to an integer");
			System.exit(1);
		}
		return 0; /* Keep compiler happy */
	}

	public static void usage()
	{
		System.err.println("wrudp: wrap UDP for tunneling with ssh");
		System.err.println();
		System.err.println("    When called without arguments: 'inside'  version is executed.");
		System.err.println();
		System.err.println("    When called with    arguments: 'outside' version is executed.");
		System.err.println("    The arguments are assumed to specify a command to spawn.");
		System.err.println("    Typically this would be a ssh session to connect to the 'inside'");
		System.err.println("    and launch an inside version of 'wrudp' there:");
		System.err.println();

		System.err.println("      java -jar wrudp.jar ssh usr@insidehost java -jar wrudp.jar");
		System.err.println();
		System.err.println("    NOTE: the following java properties must be set on the outside:");
		System.err.println("      'wrudp.dstaddr'  - host to connect to (on the inside)");
		System.err.println("      'wrudp.dstport'  - port to connect to (on the inside)");
		System.err.println("      'wrudp.lclport'  - port where to listen (on the outside)");
		System.err.println("                         if undefined 'wrudp.dstport' is used.");
		System.err.println();
		System.err.println("          the 'inside/outside' role is defined by how the local instance");
		System.err.println("          is invoked. If the local instance has no destination then 'reverse'");
		System.err.println("          operation is assumed, e.g:");
		System.err.println();
		System.err.println("            java -jar wrudp.jar -- ssh usr@outsidehost java -jar wrudp.jar -H <host> -P <port>");
		System.err.println();
		System.err.println("          keep in mind that '-H/-P/-L' are *always* given to the *outside* instance even");
		System.err.println("          though '-H/-P' define the destination on the *inside*!");
		System.err.println();
		System.err.println("    Command line options:");
		System.err.println("      -H <host>        - host to connect to (on the inside);");
		System.err.println("                         overrides 'wrupdp.dstaddr' property.");
		System.err.println("      -P <port>        - port to connect to (on the inside);");
		System.err.println("                         overrides 'wrupdp.dstport' property.");
		System.err.println("      -L <port>        - port where to listen (on the outside);");
		System.err.println("                         overrides 'wrupdp.lclport' property.");
		System.err.println("      -h               - this message.");
		System.err.println("      -d               - enable debugging messages.");
		System.err.println("      -R               - maintain only a single proxy on the 'inside'");
		System.err.println("                         and use its local port for all 'outside' connections.");
		System.err.println("                         E.g., repeated 'nc' sessions on the 'outside' talking");
		System.err.println("                         to the same 'nc' on the inside (via udp) needs this");
		System.err.println("                         because the 'server nc' remembers the source port and");
		System.err.println("                         refuses to respond to any changes of source.");
		System.err.println("      --                 ignore further options; use to pass options to command");
		System.err.println("                         and its payload.");
	}

	/* RETURNS: if 'hasArg' then 'arg' is returned if 'opt' matches 'arg'.
	 *          otherwise, if 'arg' matches 'opt' the option argument is returned.
	 *          If the option does not match, 'null' is returned.
	 */
	public static String chkopt(String arg, ArrayIterator<String> it, String opt, boolean hasArg)
	{
		if ( ! hasArg )
			return arg.equals(opt) ? arg : null;
		if ( arg.startsWith(opt) ) {
			if ( arg.length() > 2 ) {
				return arg.substring(2);
			}
			try {
				arg = it.next();
			} catch ( java.util.NoSuchElementException  e ) {
				arg = null;
			}
			if ( null == arg || arg.startsWith("-") ) {
				System.err.println("Missing option argument for " + opt);
				System.exit(1);
			}
			return arg;
		}
		return null;
	}

	public static void main(String []args)
		throws IOException
	{
	Process               p;
	IStream               istrm;
	String                dst_ip_s   = null;
	String                dst_port_s = null;
	String                lcl_port_s = null;
	int                   dst_ip;
	int                   dst_port;
	int                   lcl_port;
	InetSocketAddress     sa;
	ArrayIterator<String> opt = new ArrayIterator<String>(args);
	String                []cmd;
	int                   ncmd,nopts;
    boolean               locl, outside;
    boolean               remoteOutside = false;
    boolean               remoteInside  = false;

		nopts   = 0;
		outside = true;
		while ( opt.hasNext() ) {
			String os = opt.next();
			String oa;
			if ( null != chkopt(os, opt, "-d", false) ) {
				debug=1;
			} else
			if ( null != chkopt(os, opt, "-h", false) ) {
				usage();
				System.exit(0);
			} else
			if ( null != (oa = chkopt(os, opt, "-H", true)) ) {
				dst_ip_s = oa;
			} else 
			if ( null != (oa = chkopt(os, opt, "-P", true)) ) {
				dst_port_s = oa;
			} else
			if ( null != (oa = chkopt(os, opt, "-R", false)) ) {
				recycle_local_port = true;
				avl_proxies        = 1;
			} else
			if ( null != (oa = chkopt(os, opt, "-L", true)) ) {
				lcl_port_s = oa;
			} else
			if ( null != (oa = chkopt(os, opt, "-i", false)) ) {
				remoteInside  = true; /* FOR INTERNAL USE */
			} else
			if ( null != (oa = chkopt(os, opt, "-o", false)) ) {
				remoteOutside = true; /* FOR INTERNAL USE */
			} else
			if ( null != (oa = chkopt(os, opt, "--", false)) ) {
				break; /* following options are for CMD */
			} else {
				nopts--; /* 'opt' now points to a non-option */
				break;
			}
		}

		nopts += opt.getpos();

		ncmd  = args.length - nopts;

        cmd = null;
		if ( ( locl = ( ncmd > 0 ) ) ) {
			/* ignore -i/-o */
			remoteInside  = false;
			remoteOutside = false;
		} else {
			/* remote instance */
			if ( remoteInside == remoteOutside ) {
				System.err.println("Invalid options: -i/-o are for internal use only and inconsistent setting was detected\n");
				System.exit(1);
			}
			outside = remoteOutside;
		}

		if ( debug > 0 ) {
			System.err.println("Commands: " + ncmd + " nopts: " +nopts);
		}

		if ( null == dst_ip_s && null == ( dst_ip_s  = System.getProperty( "wrudp.dstaddr" ) ) ) {
			if ( remoteOutside ) {
				System.err.println("Need 'wrudp.dstaddr' property or '-H' option in 'outside' mode\n");
				System.exit(1);
			}
			outside = false;
		}

		if ( null == dst_port_s && null == ( dst_port_s = System.getProperty( "wrudp.dstport" ) ) ) {
			if ( remoteOutside ) {
				System.err.println("Need 'wrudp.dstport' property or '-P' in 'outside' mode\n");
				System.exit(1);
			}
			outside = false;
		}

        cmd = null;
		if ( ( locl = ( ncmd > 0 ) ) ) {

			int i;

			cmd = new String[ncmd + 1];
			for ( i = 0; i<ncmd; i++ )
				cmd[i] = args[nopts+i];

			if ( ! outside ) {
				/* If the local instance runs on the 'inside' then the outside/remote must have -L + -P
				 * set; set a flag to let the remote instance know
				 */		
				cmd[ncmd] = "-o";
			} else {
				cmd[ncmd] = "-i";
			}
		}


		if ( outside ) {

			/* outside version */

			if ( null == lcl_port_s && null == ( lcl_port_s = System.getProperty( "wrudp.lclport" ) ) ) {
				lcl_port_s = dst_port_s;
			}

			dst_port = toInt(dst_port_s);
			lcl_port = toInt(lcl_port_s);

			dst_ip = 0;
			try {
				sa     = new InetSocketAddress( dst_ip_s, dst_port );
				if ( sa.isUnresolved() ) {
					throw new java.net.UnknownHostException( dst_ip_s );
				}
				dst_ip = inet2int( sa.getAddress() );
				sa     = null; 	
			} catch (Exception e) {
				System.err.println("Unable to resolve destination: " + e);
				System.exit(1);
			} finally {
				if ( debug() > 0 ) {
					WrapUdp h = new WrapUdp();
					h.fill(0,0,dst_ip,0,0);
					h.dump();
				}
			}

			outsideSock = new UdpProxy( dst_ip, dst_port, new InetSocketAddress(lcl_port) );
		}

        if ( locl ) {
			p = Runtime.getRuntime().exec( cmd );
			ostrm = new OStream( p.getOutputStream() );
			istrm = new IStream( p.getInputStream()  );

			(new Errlog( p.getErrorStream() )).start();
		} else {
			/* remote version  */
			ostrm = new OStream();
			istrm = new IStream();
			System.err.println("UDP Tunnel is up");
		}

		istrm.run();
	}
}
