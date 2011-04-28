package tests;

import jvstm.CommitException;
import jvstm.Transaction;

import org.apache.commons.javaflow.Continuation;

import specula.jvstm.TopLevelTransaction;
import specula.jvstm.VBox;


public class SpeculativeCounter {
	
	static {
		jvstm.Transaction.setTransactionFactory(new specula.jvstm.SpeculaTransactionFactory());
	}

	static VBox<Integer> counter;
	static int iterations = 300;


	public static void main(String[] args) throws InterruptedException {
		try {
			if (args.length == 1) {
				iterations = Integer.valueOf(args[0]);
			}
		} catch (NumberFormatException e) {
			System.err.println("The only argument expected is the number of iterations.");
			System.exit(-1);
		}
		
		System.out.println("Going for " + SpeculativeCounter.iterations + " iterations");
		counter = new VBox<Integer>(0);
		
		Thread t1 = new Incrementer();
		Thread t2 = new Incrementer();

		t1.start();
		t2.start();

		t1.join();
		t2.join();

		int result = SpeculativeCounter.counter.get();
		TopLevelTransaction.sync();

		System.out.println("final counter == " + result);
	}

	public static void incSpec(int iter) {
		Continuation.suspend();
		TopLevelTransaction tx = (TopLevelTransaction) Transaction.begin();
		boolean txFinished = false;
		try {
			Integer actualValue = SpeculativeCounter.counter.get();
			SpeculativeCounter.counter.put(new Integer(actualValue + 1));

			Transaction.commit();
			txFinished = true;

			System.out.println("tx: " + tx.getNumber() +
					" - " + tx.toString() +
					" / read: " + tx._rs.first().second.version +
					" / wrote: " + (actualValue + 1) +
					" / iter: " + iter);

			return;
		} catch (CommitException ce) {
			Transaction.abort();
			txFinished = true;
		} finally {
			if (! txFinished) {
				// TODO: investigar pq não faz sentido o txFinished estar a false
				// e não haver qualquer trasacção activa
				//                    if (Transaction.current() != null) Transaction.abort();
				// NOTA: problema identificado: ler tryCommit() na TopLevelTransaction
				Transaction.abort();
			}
		}
	}
}

class Incrementer extends Thread {

	@Override
	public void run() {
		for (int i = 0; i < SpeculativeCounter.iterations; i++) {
			SpeculativeCounter.incSpec(i);
		}

		int result = SpeculativeCounter.counter.get();
		TopLevelTransaction.sync();

		System.out.println("counter == " + result);
	}

}
