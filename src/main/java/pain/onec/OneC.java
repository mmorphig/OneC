package pain.onec;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Version;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.item.Items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pain.onec.modules.*;

public class OneC extends MeteorAddon {
	public static final Logger LOG = LoggerFactory.getLogger(OneC.class);
	public static final Category Main1c = new Category("1c", Items.SNOWBALL.getDefaultStack());
	public static final String MOD_ID = "onec-addon";
	public static final ModMetadata MOD_META;
	public static final String NAME;
	public static final Version VERSION;
	public static final String BUILD_NUMBER;
	
	static { // All of this is mostly to be thorough
		MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();
		NAME = MOD_META.getName();
		
		String versionString = MOD_META.getVersion().getFriendlyString();
		if (versionString.contains("-")) versionString = versionString.split("-")[0];
		VERSION = new Version(versionString);
		
		var buildNumberValue = MOD_META.getCustomValue(MOD_ID + ":build_number");
		BUILD_NUMBER = buildNumberValue != null ? buildNumberValue.getAsString() : "local";
	}
	
	@Override
	public void onInitialize() {
		LOG.info("Initializing 1c");
		Modules.get().add(new BuildCase());
		Modules.get().add(new OneCDiscordPresence());
		Modules.get().add(new OneCFlight());
	}
	
	@Override
	public void onRegisterCategories() {
		Modules.registerCategory(Main1c);
	}
	
	public String getPackage() {
		return "pain.onec";
	}
}
