package test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;

import sim.Simulation_ClusterModelTransmission;
import sim.Simulation_MenB_Vaccine;

public class Test_MenB_Vaccine_Simulations {

	public static void main(String[] args) throws IOException, InterruptedException {
		String[] arg_def = new String[] {
				"C:\\Users\\bhui\\Documents\\Java_Test\\Test_SimClusterModel_Transmission_MenB_Vaccine_MSM",
				"-seedMap=Seed_List.csv", "-export_skip_backup" };

		String[] dirNames = new String[] {
				//"C:\\Users\\bhui\\Documents\\Java_Test\\Test_SimClusterModel_Transmission_MenB_Vaccine_MSM\\ResultSet_Baseline",
				//"C:\\Users\\bhui\\Documents\\Java_Test\\Test_SimClusterModel_Transmission_MenB_Vaccine_MSM\\ResultSet_Vac_P050_080", 
				//"C:\\Users\\bhui\\Documents\\Java_Test\\Test_SimClusterModel_Transmission_MenB_Vaccine_MSM\\ResultSet_Vac_P050_050",
				//"C:\\Users\\bhui\\Documents\\Java_Test\\Test_SimClusterModel_Transmission_MenB_Vaccine_MSM\\ResultSet_Vac_P080_000",				
				
				"C:\\Users\\bhui\\Documents\\Java_Test\\Results_MenB\\Test_SimClusterModel_Transmission_MenB_Vaccine_RMP",
				};

		for (String dirName : dirNames) {
			arg_def[0] = dirName;
			
			File[] old_zips = (new File(dirName)).listFiles(new FileFilter() {				
				@Override
				public boolean accept(File pathname) {					
					return pathname.getName().endsWith("7z");
				}
			});			
			for(File old_zip: old_zips) {
				Files.deleteIfExists(old_zip.toPath());
			}
			
			
			
			Simulation_ClusterModelTransmission.launch(arg_def, new Simulation_MenB_Vaccine());
		}
	}

}
