package pain.onec.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.world.BlockUtils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.SpawnEggItem;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import pain.onec.OneC;

/*
 * Staircase module
 * Created by mmorphig
 * 
 * Inspiration for some bits from meteor's airplace and scaffold.
 */

public class BuildCase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> heightSetting = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Number of steps in the staircase.")
        .min(1)
        .sliderRange(1, 100)
        .defaultValue(10)
        .build()
    );
    
    private final Setting<Boolean> downwards = sgGeneral.add(new BoolSetting.Builder()
        .name("downwards")
        .description("Staircase goes down, not up.")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Double> placeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("placeDistance")
        .description("Maximum distance to try to place blocks")
        .min(0.1)
        .sliderRange(0.1, 10)
        .defaultValue(4.5)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("sideColor")
        .description("The color of the sides of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("lineColor")
        .description("The color of the lines of the blocks being rendered.")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );

    public BuildCase() {
        super(OneC.Main1c, "BuildCase", "Builds a staicase.");
    }
    
    private boolean locked = false;
	private final Set<BlockPos> lockedPositions = new HashSet<>();
	private final Set<BlockPos> placedPositions = new HashSet<>();
	private boolean wasUseKeyPressed = false;

	@EventHandler
	private void onTick(TickEvent.Post event) {
	    if (mc.player == null || mc.world == null) return;
	
	    Vec3d playerEyes = mc.player.getEyePos();
	
	    boolean useKeyPressed = mc.options.useKey.isPressed();
	
	    if (useKeyPressed && !wasUseKeyPressed) {
	        lockedPositions.clear();
	        placedPositions.clear();
	        lockedPositions.addAll(generateStaircasePositions());
	        locked = true;
	    }
	
	    wasUseKeyPressed = useKeyPressed;
	
	    if (!locked) return;
	
	    FindItemResult blockItem = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem);
	    if (!blockItem.found()) {
	        info("No placeable block in hotbar.");
	        return;
	    }
	
	    BlockPos nearest = null;
	    double nearestDist = Double.MAX_VALUE;
	    Vec3d playerPos = mc.player.getPos();
	
	    for (BlockPos pos : lockedPositions) {
	        if (placedPositions.contains(pos)) continue;
	        double dist = playerPos.distanceTo(Vec3d.ofCenter(pos));
	        if (dist < nearestDist) {
	            nearestDist = dist;
	            nearest = pos;
	        }
	    }
	
	    if (nearest == null) return;
	
	    if (playerEyes.distanceTo(Vec3d.ofCenter(nearest)) > placeDistance.get()) return;
	
	    if (!mc.world.getBlockState(nearest).isAir()) {
	        placedPositions.add(nearest);
	        return;
	    }
	
	    boolean success = BlockUtils.place(nearest, blockItem, true, 50, true, true);
	    if (success) {
	        placedPositions.add(nearest);
	    }
	    if (placedPositions.size() == lockedPositions.size()) {
            locked = false;
            placedPositions.clear();
            lockedPositions.clear();
        }
	}

    @EventHandler
	private void onRender(Render3DEvent event) {
		List<BlockPos> staircase = generateStaircasePositions();

        for (BlockPos pos : staircase) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
        
	    if (!locked) return;
	
	    for (BlockPos pos : lockedPositions) {
	        boolean placed = placedPositions.contains(pos);
	        SettingColor side = placed ? new SettingColor(0, 255, 0, 30) : sideColor.get();
	        SettingColor line = placed ? new SettingColor(0, 255, 0, 200) : lineColor.get();
	
	        event.renderer.box(pos, side, line, ShapeMode.Both, 0);
	    }
	}

    private List<BlockPos> generateStaircasePositions() {
	    List<BlockPos> positions = new ArrayList<>();
	
	    Vec3d playerPos = mc.player.getPos();
	    Direction facing = getCardinalFacing(mc.player.getYaw());
	    Vec3i directionVec = facing.getVector();
	
	    Vec3d forward = new Vec3d(directionVec.getX(), 0, directionVec.getZ()).normalize().multiply(1.8);
	    Vec3d start = playerPos.add(forward);
	
	    BlockPos current = new BlockPos(
	        (int) Math.floor(start.x),
	        (int) Math.floor(start.y),
	        (int) Math.floor(start.z)
	    );
	
	    int height = heightSetting.get().intValue();
	
	    for (int i = 0; i < height; i++) {
	        positions.add(current);
	        if (downwards.get()) { 
				current = current.add(directionVec).down();
			} else {
				current = current.add(directionVec).up();
	        }
	    }
	
	    return positions;
	}
	
    private Direction getCardinalFacing(float yaw) {
        yaw = yaw % 360;
        if (yaw < 0) yaw += 360;

        if (yaw >= 45 && yaw < 135) return Direction.WEST;
        else if (yaw >= 135 && yaw < 225) return Direction.NORTH;
        else if (yaw >= 225 && yaw < 315) return Direction.EAST;
        else return Direction.SOUTH;
    }
}
