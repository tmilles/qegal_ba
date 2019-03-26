package org.softlang.qegal.process.parts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.softlang.qegal.io.IOLayer;
import org.softlang.qegal.process.regex.Dependency;
import org.softlang.qegal.process.regex.POM;
import org.softlang.qegal.process.regex.SimpleRegexMiner;
import com.google.gson.Gson;

public class SimpleRegexProcess {

	public static void mine(IOLayer iolayer, String output)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		SimpleRegexMiner miner = new SimpleRegexMiner(iolayer);
		List<POM> results = miner.mine();
		List<Dependency> dependencies = new ArrayList<>();
		for (POM p : results) {
			for (Dependency d : p.getDependencies()) {
				if (!dependencies.contains(d)) {
					dependencies.add(d);
				}
			}
		}
		FileOutputStream outputStream = new FileOutputStream("temp/output/" + output + ".json");
		dependencies.removeIf(d -> d.getVersion() == null || "".equals(d.getVersion()) || d.getVersion().matches("\\$\\{.*\\}"));
		//Filter duplicates
		List<Dependency> duplicates = new ArrayList<>();
		for(Dependency d1 : dependencies) {
			for(Dependency d2 : dependencies) {
				if(d1 != d2 && d1.getArtifactId().equals(d2.getArtifactId()) && d1.getGroupId().equals(d2.getGroupId())) {
					boolean alreadyInList = duplicates.stream().filter(d -> d.getArtifactId().equals(d1.getArtifactId()) && d.getGroupId().equals(d1.getGroupId())).toArray().length > 0;
					if(!alreadyInList) {
						duplicates.add(d1);
					}
				}
			}
		}
		dependencies.removeAll(duplicates);
		System.out.println("Could fully resolve: " + dependencies.size());
		Gson gson = new Gson();
		String json = gson.toJson(dependencies);
		System.out.println(json);
		byte[] strToBytes = json.getBytes();
		outputStream.write(strToBytes);
		outputStream.close();
		miner = null;
	}
}
