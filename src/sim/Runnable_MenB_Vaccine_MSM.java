package sim;

import java.util.Properties;
import java.util.regex.Pattern;

import relationship.ContactMap;

public class Runnable_MenB_Vaccine_MSM extends Runnable_ClusterModel_MultiTransmission {
	
	public static final Pattern PROP_TYPE_PATTERN = Pattern.compile("ClusterModel_MenB_Vaccine_MSM");
	
	private static final int num_inf = 1; // NG only
	private static final int num_site = 4;
	private static final int num_act = 5;

	public Runnable_MenB_Vaccine_MSM(long cMap_seed, long sim_seed, ContactMap base_cMap, Properties prop) {
		super(cMap_seed, sim_seed, base_cMap, prop, num_inf, num_site, num_act);
		
	}

}
