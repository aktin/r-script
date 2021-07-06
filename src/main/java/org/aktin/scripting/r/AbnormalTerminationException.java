package org.aktin.scripting.r;

/**
 * Rscript process terminated abnormally (exit code != 0).
 * The corresponding STDERR output can be obtained via
 * @author R.W.Majeed
 *
 */
public class AbnormalTerminationException extends Exception{

	private static final long serialVersionUID = 1L;

	private int exitCode;
	private String stderrOutput;

	protected AbnormalTerminationException(int exitCode, String stderr, boolean debugging) {
		super(firstLineOr250Characters(stderr, debugging));
		this.exitCode = exitCode;
		this.stderrOutput = stderr;
		// use first line of error output for exception message
	}

	private static final String firstLineOr250Characters(String msg, boolean debugging) {
		if(debugging)
			return msg;
		int eol = msg.indexOf('\n');
		eol = Math.min((eol==-1)?msg.length():eol , 1000);
		return msg.substring(0, eol);
	}
	/**
	 * Get the process' exit code. Since it was terminated abnormally, the code should be non-zero.
	 * @return exit code
	 */
	public int getExitCode() {
		return exitCode;
	}

	public String getErrorOutput() {
		return stderrOutput;
	}
}
