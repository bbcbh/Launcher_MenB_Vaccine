package sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

import person.AbstractIndividualInterface;
import random.MersenneTwisterRandomGenerator;
import random.RandomGenerator;
import util.PropValUtils;

public class Runnable_MenB_Vaccine extends Runnable_MetaPopulation_MultiTransmission {

	public static final String PROP_VACCINE_PROPROPTIES = "PROP_VACCINE_PROPROPTIES";
	public static final String PROP_VACCINE_ALLOCATIONS = "PROP_VACCINE_ALLOCATIONS";

	protected static final String SIM_OUTPUT_KEY_VACC_COVERAGE = "SIM_OUTPUT_KEY_VACC_COVERAGE";
	protected static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED = 0;
	protected static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES = SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED
			+ 1;
	protected static final int SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS = SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES
			+ 1;
	protected static final int LENGTH_SIM_OUTPUT_INDEX_VACC_COVERAGE = SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS
			+ 1;

	// Vaccine

	// Usage: double[]
	// {Dose_0_Site_0_Eff, Dose_0_Site_1_Eff, ...
	// Dose_0_Site_0_Waning_Rate_Per_Year, Dose_0_Site_1_Waning_Rate_Per_Year, ....
	// Dose_1_Site_0, ...]
	protected double[] vaccine_properties;

	// Usage: double[][]
	// { GLOBAL_START,RISK_GRP_INC, PROB_DOSE_0_AT_TEST,
	// PROB_NEXT_DOSE_0, NEXT_DOSE_AT_0,
	// PROB_NEXT_DOSE_1, NEXT_DOSE_AT_1,...}

	protected double[][] vaccine_allocate_all_default;

	public static final int VACCINE_ALLOCATE_GLOBAL_START = 0;
	public static final int VACCINE_ALLOCATE_GRP_INC = VACCINE_ALLOCATE_GLOBAL_START + 1;

	// Key = PID , Val = Dose_time
	protected HashMap<Integer, ArrayList<Integer>> vaccination_history = new HashMap<>();
	// Key = Time , Val = PIDS
	protected HashMap<Integer, ArrayList<Integer>> schedule_booster = new HashMap<>();

	// RNG
	protected RandomGenerator rng_vaccine;

	public Runnable_MenB_Vaccine(long cMap_seed, long sim_seed, Properties prop,
			int num_inf, int num_site, int num_act) {
		super(cMap_seed, sim_seed, prop, num_inf, num_site, num_act);

		vaccine_properties = (double[]) PropValUtils.propStrToObject(
				prop.getProperty(PROP_VACCINE_PROPROPTIES, Arrays.toString(new double[0])), double[].class);

		vaccine_allocate_all_default = (double[][]) PropValUtils.propStrToObject(
				prop.getProperty(PROP_VACCINE_ALLOCATIONS, Arrays.toString(new double[0][0])), double[][].class);

		rng_vaccine = new MersenneTwisterRandomGenerator(sim_seed);
	}

	@Override
	protected double getTransmissionProb(int currentTime, int inf_id, int pid_inf_src, int pid_inf_tar,
			int partnershiptDur, int actType, int src_site, int tar_site) {
		double trans_prob = super.getTransmissionProb(currentTime, inf_id, pid_inf_src, pid_inf_tar, partnershiptDur,
				actType, src_site, tar_site);

		// Protective efficiency
		if (vaccination_history.containsKey(pid_inf_tar)) {
			ArrayList<Integer> dose_time_hist = vaccination_history.get(pid_inf_tar);
			
			// vaccine_properties: double[]
			// {Dose_0_Site_0_Eff, Dose_0_Site_1_Eff, ...
			//  Dose_0_Site_0_Waning_Rate_Per_Year, Dose_0_Site_1_Waning_Rate_Per_Year, ....
			//  Dose_1_Site_0_Eff ....}
			
			int dose_pt = (dose_time_hist.size() - 1) * (this.NUM_SITE * 2);

			// Use the stat from last dose
			while (dose_pt > vaccine_properties.length) {
				dose_pt -= this.NUM_SITE * 2;
			}

			double rate_wane_per_year = vaccine_properties[dose_pt + this.NUM_SITE + tar_site];
			double vacc_eff = vaccine_properties[dose_pt + tar_site];

			vacc_eff *= Math.exp((rate_wane_per_year * (currentTime - dose_time_hist.get(dose_time_hist.size() - 1)))
					/ AbstractIndividualInterface.ONE_YEAR_INT);

			trans_prob *= (1 - vacc_eff);
		}

		return trans_prob;
	}
	
	
	

}
