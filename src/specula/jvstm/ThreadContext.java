package specula.jvstm;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import specula.core.SpeculaTransaction;
import specula.core.TransactionStatus;
import contlib.Continuation;


public class ThreadContext extends specula.core.ThreadContext {

	private final List<SpeculaTransaction> _transactions;
	private Continuation _lastContinuation;
	// o volatile obriga à sincronização entre a thread de validação e a
	// que está a olhar para o field
	private volatile SpeculaTransaction _abortedTx;

	private final Thread _startingThread;


	public ThreadContext() {
		_transactions = new LinkedList<SpeculaTransaction>();

		_startingThread = Thread.currentThread();
	}

	public Thread getThread() {
		return _startingThread;
	}

	void addTransaction(SpeculaTransaction tx) {
		assert (_startingThread == Thread.currentThread());

		_transactions.add(tx);
	}

	Collection<SpeculaTransaction> getTransactions() {
		assert (_startingThread == Thread.currentThread());

		return _transactions;
	}

	public Continuation getLastContinuation() {
		return _lastContinuation;
	}

	public void setLastContinuation(Continuation c) {
		_lastContinuation = c;
	}

	public boolean hasTransactionAborted() {
		synchronized (this) {
			return ! (_abortedTx == null);
		}
	}

	public void setAbortedTransaction(SpeculaTransaction tx) {
		synchronized (this) {
			if (_abortedTx == null) _abortedTx = tx;
		}
	}

	public boolean isTheAbortedTransaction(SpeculaTransaction tx) {
		synchronized (this) {
			return (_abortedTx == tx);
		}
	}

	public void syncThreadContext() {
		assert (_startingThread == Thread.currentThread());

		Iterator<SpeculaTransaction> it = getTransactions().iterator();
		boolean abort = false;

		while (it.hasNext()) {
			TopLevelTransaction tx = (TopLevelTransaction) it.next();

			if (abort) {
				tx.abortTx();
			} else {
				synchronized (tx) {
					while (tx.getStatus() == TransactionStatus.COMPLETE) {
						try {
							tx.wait();
						} catch (InterruptedException e) {	}
					}
				}

				if (tx.getStatus() == TransactionStatus.TO_ABORT) {
					assert (isTheAbortedTransaction(tx));
					tx.abortTx();
					abort = true;
				}
			}
		}
		getTransactions().clear();

		if (abort) {
			Continuation resumePoint = _abortedTx.getResumePoint();
			synchronized (this) {
				_abortedTx = null;	
			}
			_lastContinuation = null;

			Continuation.resume(resumePoint);
		} else {
			_lastContinuation = null;
		}
	}

}
