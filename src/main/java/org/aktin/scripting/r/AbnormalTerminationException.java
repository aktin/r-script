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
		this.exitCode = exitCode;
		if(debugging)
			this.stderrOutput = stderr;
		// use first line of error output for exception message
		else
			this.stderrOutput = firstLineOr250Characters(stderr);
	}

	private static String firstLineOr250Characters(String msg) {
		int eol = -1;//msg.indexOf('\n');
		eol = Math.min((eol==-1)?msg.length():eol , 250);
		String excerpt = msg.substring(0, eol);
		return excerpt.replace("\r","").replace("\n", "\\n");
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
