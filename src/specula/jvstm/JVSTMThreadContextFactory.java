package specula.jvstm;

import specula.core.ThreadContext;
import specula.core.ThreadContextFactory;

public class JVSTMThreadContextFactory implements ThreadContextFactory {

	@Override
	public ThreadContext makeNew() {
		return new specula.jvstm.ThreadContext();
	}

}
