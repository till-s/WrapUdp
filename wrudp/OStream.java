package wrudp;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.Channels;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStream;

class OStream {
	protected WritableByteChannel chnl;
	protected OutputStream        os;

	class IncompleteBufferWrittenException extends Exception {
		int put, wanted;
		protected IncompleteBufferWrittenException(int put, int wanted)
		{
			super( "Buffer not completely written (only " + put + " out of " + wanted + " bytes)");
			this.put    = put;
			this.wanted = wanted;
		}
	}

	public OStream()
	{
		chnl = new FileOutputStream(java.io.FileDescriptor.out).getChannel();
		os   = null;
	}

	public OStream(OutputStream os)
	{
		chnl    = Channels.newChannel( os );
		this.os = os;
	}

	public void write(ByteBuffer b)
		throws IOException, IncompleteBufferWrittenException
	{
	int here = b.position();

		while ( b.remaining() > 0 && chnl.write(b) > 0 )
			/* noting else to do */;
		if ( b.remaining() > 0 )
			throw new IncompleteBufferWrittenException(b.position() - here, b.limit());
	}

	public void flush()
		throws IOException
	{
		if ( null != os ) {
			os.flush();
		}
	}
}
