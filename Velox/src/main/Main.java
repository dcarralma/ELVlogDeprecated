package main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import uk.ac.ox.cs.JRDFox.JRDFStoreException;
import uk.ac.ox.cs.JRDFox.store.DataStore;
import uk.ac.ox.cs.JRDFox.store.TupleIterator;
import velox.VeloxReasoner;

public class Main {

	public static void main(String[] arguments)
			throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, JRDFStoreException {

		System.out.println("Arguments: " + arguments[0] + " " + arguments[1] + " " + arguments[2] + " " + arguments[3] + "\n");

		int threads = Integer.parseInt(arguments[0]);
		String ontologyTBoxFilePath = arguments[1];
		String turtleDirPath = arguments[2];
		String prefix = arguments[3];

		ArrayList<String> turtleFileNames = new ArrayList<String>();
		for (String turtleFileName : new File(turtleDirPath).list())
			if (!turtleFileName.equals(".DS_Store"))
				turtleFileNames.add(turtleDirPath + File.separator + turtleFileName);
		File[] turtleFiles = new File[turtleFileNames.size()];
		for (int i = 0; i < turtleFileNames.size(); i++)
			turtleFiles[i] = new File(turtleFileNames.get(i));

		long start = System.nanoTime();
		System.out.println("  > Initializing Reasoner");
		VeloxReasoner velox = new VeloxReasoner(ontologyTBoxFilePath, turtleFiles, DataStore.StoreType.NarrowParallelHead, threads);
		System.out.println("   * Files Loaded (" + velox.getTriplesCount() + "): " + ((System.nanoTime() - start) / 1000000000) + "s");

		System.out.println("  > Reasoning");
		start = System.nanoTime();
		velox.decideSatisfiability();
		System.out.println("   * Satisfiable ontology: " + velox.isSatisfiable());
		System.out.println(
				"   * Materialization Complete (" + velox.getTriplesCount() + "): " + ((System.nanoTime() - start) / 1000000000) + "s");

		String rdfType = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
		System.out.println(
				"  > Query Results: " + size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + rdfType + " <" + prefix + "#Query1> } "))
						+ " / " + size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + rdfType + " <" + prefix + "#Query2> } ")) + " / "
						+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + rdfType + " <" + prefix + "#Query3> } ")) + " / "
						+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + rdfType + " <" + prefix + "#Query4> } ")) + " / "
						+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + rdfType + " <" + prefix + "#Query5> } ")) + " / "
						+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + rdfType + " <" + prefix + "#Query6> } ")) + " / "
						+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + rdfType + " <" + prefix + "#Query7> } ")) + " / "
						+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + rdfType + " <" + prefix + "#Query8> } ")));

		System.out.println("\n");
	}

	private static int size(TupleIterator tupleIterator) throws JRDFStoreException {
		int counter = 0;
		for (long multiplicity = tupleIterator.open(); multiplicity != 0; multiplicity = tupleIterator.getNext())
			counter++;
		return counter;
	}
}
