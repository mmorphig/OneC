package pain.onec.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;

import pain.onec.OneC;

/* 
 * AutoWither module
 * By mmorphig
 */

public class AutoWither extends Module {
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	
	private final Setting<Boolean> breakBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("break-blocks")
        .description("Attemps to break non-replaceable blocks if there are any in the way")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Boolean> autoPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("full-auto")
        .description("Automatically places withers on an interval, flight is recommended.")
        .defaultValue(false)
        .build()
    );
    
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The amount of delay, in ticks, between each wither placement. 43 is enough of a delay that the last one is out of the way.")
        .defaultValue(20)
        .min(1)
        .sliderMax(200)
        .visible(() -> autoPlace.get() == true)
        .build()
    );
    
    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("sideColor")
        .description("The color of the sides of the center block's ghost.")
        .defaultValue(new SettingColor(204, 0, 0, 10))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("lineColor")
        .description("The color of the lines of the center block's ghost.")
        .defaultValue(new SettingColor(204, 0, 0, 255))
        .build()
    );
    
    private boolean wasUseKeyPressed = false;
    private BlockPos basePos;
    private int delayLeft = delay.get();

    public AutoWither() {
        super(OneC.Main1c, "auto-wither", "Places a Wither in front of you with the click of a button! (the right one)");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (delayLeft > 0 && autoPlace.get()) delayLeft--;
        
        Direction facing = getCardinalFacing(mc.player.getYaw());
        basePos = mc.player.getBlockPos().offset(facing, 2).up();

        boolean useKeyPressed = mc.options.useKey.isPressed();

        if (useKeyPressed && !wasUseKeyPressed) {
            tryPlaceWither();
        } else if (delayLeft <= 0) {
			tryPlaceWither();
			delayLeft = delay.get();
		}

        wasUseKeyPressed = useKeyPressed;
    }
    
    @Override
	public void onDeactivate() {
	    basePos = null;
	}
    
    @EventHandler
	private void onRender(Render3DEvent event) {
		if (basePos == null) return;
		event.renderer.box(basePos, sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
	}
    
    private void clearBlocks(BlockPos origin, Direction facing, boolean onlyReplaceable) {
	    Direction left = facing.rotateYCounterclockwise();
	    Direction right = facing.rotateYClockwise();
	
	    BlockPos center = origin;
	    BlockPos down = center.down();
	    BlockPos leftArm = center.offset(left);
	    BlockPos rightArm = center.offset(right);
	
	    BlockPos skullCenter = center.up();
	    BlockPos skullLeft = leftArm.up();
	    BlockPos skullRight = rightArm.up();
	
	    BlockPos spaceRight = rightArm.down();
	    BlockPos spaceLeft = leftArm.down();
	
	    BlockPos[] toCheck = new BlockPos[] {
	        center, down, leftArm, rightArm,
	        skullCenter, skullLeft, skullRight,
	        spaceRight, spaceLeft
	    };
	
	    for (BlockPos pos : toCheck) {
	        BlockState state = mc.world.getBlockState(pos);
	        if (state.isReplaceable() || !onlyReplaceable) {
	            BlockUtils.breakBlock(pos, true);
	        }
	    }
	}

    private void tryPlaceWither() {
        FindItemResult soulSand = InvUtils.findInHotbar(i -> i.getItem() == Items.SOUL_SAND);
        FindItemResult skull = InvUtils.findInHotbar(i -> i.getItem() == Items.WITHER_SKELETON_SKULL);

        if (!soulSand.found() || !skull.found()) {
            info("Missing Items.");
            return;
        }

        Direction facing = getCardinalFacing(mc.player.getYaw());

        // Check for air and clears non-air
        if (!isAreaClear(basePos, facing)) {
			clearBlocks(basePos, facing, true);
	        if (breakBlocks.get()) {
	            clearBlocks(basePos, facing, false);
	
	            if (!isAreaClear(basePos, facing)) {
	                warning("Not enough space to place Wither, even after breaking replaceable blocks.");
	                return;
	            }
	        } else {
	            warning("Not enough space to place Wither. Enable block breaking to clear area.");
	            return;
	        }
	    }

        BlockPos center = basePos;
        BlockPos left = center.offset(facing.rotateYCounterclockwise());
        BlockPos right = center.offset(facing.rotateYClockwise());

        boolean b1 = BlockUtils.place(center, soulSand, true, 50, true, true);
        boolean b2 = BlockUtils.place(center.down(), soulSand, true, 50, true, true);
        boolean b3 = BlockUtils.place(left, soulSand, true, 50, true, true);
        boolean b4 = BlockUtils.place(right, soulSand, true, 50, true, true);

        if (b1 && b2 && b3 && b4) {
            BlockUtils.place(left.up(), skull, true, 50, true, true);
            BlockUtils.place(center.up(), skull, true, 50, true, true);
            BlockUtils.place(right.up(), skull, true, 50, true, true);
        } else {
            warning("Failed to place soul sand blocks.");
        }
    }

    private boolean isAreaClear(BlockPos origin, Direction facing) {
	    Direction left = facing.rotateYCounterclockwise();
	    Direction right = facing.rotateYClockwise();
	
	    BlockPos center = origin;
	    BlockPos down = center.down();
	    BlockPos leftArm = center.offset(left);
	    BlockPos rightArm = center.offset(right);
	
	    BlockPos skullCenter = center.up();
	    BlockPos skullLeft = leftArm.up();
	    BlockPos skullRight = rightArm.up();
	    
	    BlockPos spaceRight = rightArm.down();
	    BlockPos spaceLeft = leftArm.down();
	
	    BlockPos[] toCheck = new BlockPos[] {
	        center, down, leftArm, rightArm,
	        skullCenter, skullLeft, skullRight,
	        spaceRight, spaceLeft
	    };
	
	    for (BlockPos pos : toCheck) {
	        if (!mc.world.getBlockState(pos).isAir()) return false;
	    }
	    return true;
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
