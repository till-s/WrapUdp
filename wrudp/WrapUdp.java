package wrudp;

import java.nio.ByteBuffer;

import java.io.IOException;

class WrapUdp {

	class WrapUdpBadVersionException extends Exception {
		WrapUdpBadVersionException (int bad_vers) {
			super ( "Unsupported WrapUdp version: " + bad_vers + " (expecting " + WRAP_UDP_VERSN_1 + ")" ); 
		}
	}

	protected static final int WRAP_UDP_VERSN_1   = 1;
	protected static final int WRAP_UDP_VERSN_OFF = 0;
	protected static final int WRAP_UDP_PLDLN_OFF = 2;
	protected static final int WRAP_UDP_SADDR_OFF = 4;
	protected static final int WRAP_UDP_DADDR_OFF = 8;
	protected static final int WRAP_UDP_SPORT_OFF = 12;
	protected static final int WRAP_UDP_DPORT_OFF = 14;
	protected static final int WRAP_UDP_HEADR_SIZ = 16;

	protected static final int WRAP_UDP_PLDLN_MAX = 65536;

	protected ByteBuffer b;

	public WrapUdp()
	{ b = ByteBuffer.allocate( WRAP_UDP_HEADR_SIZ ); }

	public int get_pldln() { b.position( WRAP_UDP_PLDLN_OFF ); return ((b.getShort()) & 0xffff); }
	public int get_saddr() { b.position( WRAP_UDP_SADDR_OFF ); return   b.getInt();              }
	public int get_daddr() { b.position( WRAP_UDP_DADDR_OFF ); return   b.getInt();              }
	public int get_sport() { b.position( WRAP_UDP_SPORT_OFF ); return ((b.getShort()) & 0xffff); }
	public int get_dport() { b.position( WRAP_UDP_DPORT_OFF ); return ((b.getShort()) & 0xffff); }

	public ByteBuffer fill(int size, int saddr, int daddr, int sport, int dport)
	{
		b.clear();
		b.put( (byte) WRAP_UDP_VERSN_1 );
		b.position( WRAP_UDP_PLDLN_OFF );
		b.putShort( (short) size );
		b.putInt  (         saddr );
		b.putInt  (         daddr );
		b.putShort( (short) sport );
		b.putShort( (short) dport );
		b.flip();
		return b;
	}

	public void write(OStream s)
		throws IOException, OStream.IncompleteBufferWrittenException
	{
		b.rewind();
		s.write(b);
	}

	public void read(IStream s)
		throws IOException, IStream.IncompleteBufferReadException, WrapUdpBadVersionException
	{
	int version;

		b.clear();
		s.read(b);
		b.flip();

		version = b.get();

		if ( version != WRAP_UDP_VERSN_1 ) {
			throw new WrapUdpBadVersionException( version );
		}
	}

	protected void dmpa(int pos)
	{
		b.position( pos );
		System.err.format(
			"%d.%d.%d.%d",
			((int)b.get()) & 0xff,
			((int)b.get()) & 0xff,
			((int)b.get()) & 0xff,
			((int)b.get()) & 0xff
		);
	}

	public void dump()
	{
	int here = b.position();
		b.position( WRAP_UDP_VERSN_OFF );
		System.err.println("Version: " + b.get());
		System.err.println("Payload: " + get_pldln() + " bytes");
		System.err.format ("Srcaddr: ");
		dmpa( WRAP_UDP_SADDR_OFF );
		System.err.format (":%d\n", get_sport());
		System.err.format ("Dstaddr: ");
		dmpa( WRAP_UDP_DADDR_OFF );
		System.err.format (":%d\n", get_dport());
		b.position( here );
	}
}
