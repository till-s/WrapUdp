package wrudp;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.Channels;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;

class IStream {
	protected ReadableByteChannel chnl;

	class IncompleteBufferReadException extends Exception {
		int got, wanted;
		protected IncompleteBufferReadException(int got, int wanted)
		{
			super( "Buffer not completely read (only " + got + " out of " + wanted + " bytes)");
			this.got    = got;
			this.wanted = wanted;
		}
	}

	public IStream()
	{
		chnl = new FileInputStream(java.io.FileDescriptor.in).getChannel();
	}

	public IStream( InputStream is )
	{
		chnl = Channels.newChannel( is );
	}

	public void read(ByteBuffer b)
		throws IOException, IncompleteBufferReadException
	{
	int here = b.position();
	int got;
		while ( b.remaining() > 0 && ( got = chnl.read(b) ) > 0 )
			/* noting else to do */;
		if ( b.remaining() > 0 )
			throw new IncompleteBufferReadException(b.position() - here, b.limit());
	}

	public void run()
	{
	WrapUdp    hdr = new WrapUdp();
	ByteBuffer pld = ByteBuffer.allocate( WrapUdp.WRAP_UDP_PLDLN_MAX );
	UdpProxy   prx;

		try {
			while ( true ) {
				if ( UdpProxy.debug() > 0 ) {
					System.err.println("Waiting for header:");
				}
				hdr.read(this);
				if ( UdpProxy.debug() > 0 ) {
					System.err.println("Got header:");
					hdr.dump();
				}
				pld.clear();
				pld.limit( hdr.get_pldln() );
				read( pld );
				pld.flip();
				UdpProxy.send( hdr, pld );
			}
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			System.exit(1);
		}
	}
}
