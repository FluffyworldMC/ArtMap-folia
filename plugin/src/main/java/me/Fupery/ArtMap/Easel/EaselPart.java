package me.Fupery.ArtMap.Easel;

import me.Fupery.ArtMap.ArtMap;
import static org.bukkit.entity.EntityType.ARMOR_STAND;
import static org.bukkit.entity.EntityType.ITEM_FRAME;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;

import me.Fupery.ArtMap.api.Config.Lang;

/** Represents a part of an easel object */
public enum EaselPart {
	STAND(ARMOR_STAND, 0.4, -1, true, Material.OAK_FENCE), FRAME(ITEM_FRAME, 1, 0, false, null), SIGN(ARMOR_STAND, 0, 0, false, null), SEAT(ARMOR_STAND,
			ArtMap.instance().getBukkitVersion().getVersion().getSeatXOffset(), ArtMap.instance().getBukkitVersion().getVersion().getSeatYOffset(),
			true, null), MARKER(ARMOR_STAND, SEAT.modifier, 0, true, null);

	public static final String ARBITRARY_SIGN_ID = "*{=}*";
	public static final String EASEL_ID = "Easel";
	public static final Material BASE_MATERIAL = Material.OAK_SLAB;

	final EntityType entityType;
	final double modifier;
	final double heightOffset;
	final boolean centred;
	final Material blockType;

	EaselPart(EntityType entityType, double modifier, double heightOffset, boolean centred) {
		this(entityType, modifier, heightOffset, centred, null);
	}

	EaselPart(EntityType entityType, double modifier, double heightOffset, boolean centred, Material blockType) {
		this.entityType = entityType;
		this.modifier = modifier;
		this.heightOffset = heightOffset;
		this.centred = centred;
		this.blockType = blockType;
	}

	public static EaselPart getPartType(Entity entity) {
		switch (entity.getType()) {
		case ARMOR_STAND:
			ArmorStand stand = (ArmorStand) entity;
			if (EASEL_ID.equals(stand.getCustomName())) return STAND;
			return stand.isVisible() ? STAND : (stand.isSmall() ? MARKER : SEAT);
		case ITEM_FRAME:
			return FRAME;
		default:
			break;
		}
		return null;
	}

	public static EaselPart getPartType(Block block) {
		return block != null && block.getType() == Material.OAK_FENCE ? STAND : null;
	}

	public Material getBlockType() {
		return blockType;
	}

	/** The oak-fence base block, directly below the easel sign. */
	public static Location getBaseLocation(Location easelLocation) {
		return easelLocation == null ? null : easelLocation.clone().subtract(0, 1, 0);
	}

	/** The easel sign location for a clicked base block. */
	public static Location getEaselLocationFromBase(Location baseLocation) {
		return baseLocation == null ? null : baseLocation.clone().add(0, 1, 0).getBlock().getLocation();
	}

	/** The block under the seated painter, where the easel's Oak Slab foot belongs. */
	public static Location getSeatSlabLocation(Location easelLocation, BlockFace facing) {
		if (easelLocation == null || facing == null) return null;
		Location seat = SEAT.getPartPos(easelLocation, facing);
		return new Location(easelLocation.getWorld(), seat.getBlockX(),
				easelLocation.getBlockY() - 1, seat.getBlockZ());
	}

	public static BlockFace getFacing(double yaw) {

		switch ((int) yaw) {
		case 0:
			return BlockFace.SOUTH;
		case 90:
			return BlockFace.WEST;
		case 180:
			return BlockFace.NORTH;
		case 270:
			return BlockFace.EAST;
		}
		return BlockFace.SOUTH;
	}

	private static BlockFace getSignFacing(BlockFace facing) {
		BlockFace orientation = facing.getOppositeFace();
		return orientation;
	}

	public static int getYawOffset(BlockFace face) {

		switch (face) {

		case SOUTH:
			return 180;

		case WEST:
		case EAST:
			return 90;
		
		default: // NORTH et. al.
		    return 0;
		}
	}

	private EntityType getType() {
		return this.entityType;
	}

	public Entity spawn(Location easelLocation, BlockFace facing) {

		if (this == SIGN) {
			easelLocation.getBlock().setType(ArtMap.instance().getBukkitVersion().getVersion().getWallSign());
			WallSign bd = (WallSign) easelLocation.getBlock().getBlockData();
			bd.setFacing(getSignFacing(facing));
			easelLocation.getBlock().setBlockData(bd, false);

			Sign sign = ((Sign) easelLocation.getBlock().getState());
			sign.setLine(3, ARBITRARY_SIGN_ID);
			sign.update(true, false);

		} else if (blockType != null) {
			Location base = getBaseLocation(easelLocation);
			base.getBlock().setType(blockType);
			easelLocation.getBlock().setType(blockType);
			Location seatSlab = getSeatSlabLocation(easelLocation, facing);
			if (seatSlab != null) seatSlab.getBlock().setType(BASE_MATERIAL);
			return null;

		} else {
			Location partPos = getPartPos(easelLocation, facing);

			if (this == SEAT || this == MARKER || partPos.getBlock().isEmpty()) {
				Entity entity = easelLocation.getWorld().spawnEntity(partPos, getType());

				switch (this) {

				case STAND:
					ArmorStand stand = (ArmorStand) entity;
					stand.setBasePlate(false);
					stand.setCustomNameVisible(false);
					stand.setCustomName(EASEL_ID);
					stand.setGravity(false);
					stand.setRemoveWhenFarAway(false);
					stand.setArms(false);
					return stand;

				case FRAME:
					ItemFrame frame = (ItemFrame) entity;
					frame.setFacingDirection(facing, true);
					frame.setCustomNameVisible(false);
					return frame;

				case SEAT:
					ArmorStand seat = (ArmorStand) entity;
					seat.setVisible(false);
					seat.setGravity(false);
					seat.setArms(false);
					seat.setRemoveWhenFarAway(true);
					return seat;

				case MARKER:
					ArmorStand marker = (ArmorStand) entity;
					marker.setVisible(false);
					marker.setGravity(false);
					marker.setRemoveWhenFarAway(true);
					marker.setSmall(true);
					return marker;
				default:
				}
			}
		}
		return null;
	}

	private Location getOffset(World world, BlockFace facing) {
		double x = 0, z = 0;
		float yaw = 0;

		switch (facing) {
		case NORTH:
			z = -this.modifier;
			yaw = 180;
			break;
		case SOUTH:
			z = this.modifier;
			yaw = 0;
			break;
		case WEST:
			x = -this.modifier;
			yaw = 90;
			break;
		case EAST:
			x = this.modifier;
			yaw = 270;
			break;
		default:
		}

		if (this.centred) {
			x += 0.5;
			z += 0.5;
		}
		return new Location(world, x, this.heightOffset, z, yaw, 0);
	}

	private Location getPartPos(Location easelLocation, BlockFace facing) {
		Location offset = getOffset(easelLocation.getWorld(), facing);
		float yaw = (this == SEAT) ? offset.getYaw() + 180 : offset.getYaw();
		Location partLocation = easelLocation.clone().add(offset);
		partLocation.setYaw(yaw);
		return partLocation;
	}

	public Location getEaselPos(Location partLocation, BlockFace facing) {
		Location offset = getOffset(partLocation.getWorld(), facing);
		Location easelLocation = partLocation.clone().subtract(offset);
		easelLocation.setYaw(offset.getYaw());
		return easelLocation.getBlock().getLocation();
	}
}
