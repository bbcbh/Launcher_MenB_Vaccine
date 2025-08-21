package sim;

import java.io.IOException;
import java.util.Properties;

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
			return new Runnable_MenB_Vaccine_MSM(cMap_seed, sim_seed,
					baseContactMapMapping.get(cMap_seed), loadedProperties);
		}else {
			return null;
		}
	}

}
