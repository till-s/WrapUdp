package wrudp;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/* Trivial class extending 'Thread' to copy
 * characters from an InputStream to an
 * OutputStream
 *
 * Typically used to copy stderr of a spawned
 * process (Runtime.exec()) to the VM stderr.
 */

class Errlog extends Thread {

	protected InputStream  is;
	protected OutputStream os;

	public Errlog(InputStream is)
	{
		this(is, System.err);
	}

	public Errlog(InputStream is, OutputStream os)
	{
		this.is = is;
		this.os = os;
	}

	public void run()
	{
		byte []buf=new byte[1];
		try {
			while ( 1 == is.read(buf) ) {
				os.write(buf);
			}
		} catch (IOException e) {
			e.printStackTrace( System.err );
		} finally {
			System.err.println("Errlog: terminated");
		}
	}
}
