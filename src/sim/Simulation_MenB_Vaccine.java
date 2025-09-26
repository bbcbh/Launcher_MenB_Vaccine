package sim;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import relationship.ContactMap;

public class Simulation_MenB_Vaccine extends Simulation_ClusterModelTransmission {

	public static void main(String[] args) throws IOException, InterruptedException {
		final String USAGE_INFO = String.format(
				"Usage: java %s - PROP_FILE_DIRECTORY "
						+ "<-export_skip_backup> <-printProgress> <-seedMap=SEED_MAP>\n",
				Simulation_MenB_Vaccine.class.getName());
		if (args.length < 1) {
			System.out.println(USAGE_INFO);
			System.exit(0);
		} else {
			Simulation_ClusterModelTransmission.launch(args, new Simulation_MenB_Vaccine());
		}

	}

	@Override
	public Abstract_Runnable_ClusterModel_Transmission generateDefaultRunnable(long cMap_seed, long sim_seed,
			Properties loadedProperties) {

		String popType = (String) loadedProperties
				.get(SimulationInterface.PROP_NAME[SimulationInterface.PROP_POP_TYPE]);

		if (Runnable_MenB_Vaccine_MSM.PROP_TYPE_PATTERN.matcher(popType).matches()) {
			return new Runnable_MenB_Vaccine_MSM(cMap_seed, sim_seed, baseContactMapMapping.get(cMap_seed),
					loadedProperties);
		} else {
			return null;
		}
	}
	
		
	@Override
	protected void loadAllContactMap(ArrayList<File> preGenClusterMap, HashMap<Long, ArrayList<File>> cmap_file_collection,
			HashMap<Long, ContactMap> cMap_Map) throws FileNotFoundException, IOException, InterruptedException {

		// Single load only
		for (File element : preGenClusterMap) {
			System.out.printf("Loading (in series) on ContactMap located at %s.\n", element.getAbsolutePath());
			Matcher m = Pattern.compile(REGEX_ALL_CMAP).matcher(element.getName());
			m.matches();
			long cMap_seed = Long.parseLong(m.group(1));
			ContactMap cMap = extractedCMapfromFile(element);
			cMap_Map.put(cMap_seed, cMap);
			cmap_file_collection.put(cMap_seed, new ArrayList<File>(List.of(element)));

		}

	}

}
