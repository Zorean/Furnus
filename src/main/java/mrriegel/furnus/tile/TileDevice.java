package mrriegel.furnus.tile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;

import cofh.redstoneflux.api.IEnergyReceiver;
import mrriegel.furnus.Furnus;
import mrriegel.furnus.gui.ContainerDevice;
import mrriegel.furnus.init.ModConfig;
import mrriegel.furnus.init.ModItems;
import mrriegel.furnus.util.Enums.Direction;
import mrriegel.furnus.util.Enums.Mode;
import mrriegel.furnus.util.Enums.Upgrade;
import mrriegel.limelib.LimeLib;
import mrriegel.limelib.block.CommonBlock;
import mrriegel.limelib.helper.InvHelper;
import mrriegel.limelib.helper.NBTHelper;
import mrriegel.limelib.helper.StackHelper;
import mrriegel.limelib.tile.CommonTileInventory;
import mrriegel.limelib.util.EnergyStorageExt;
import mrriegel.limelib.util.Utils;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.api.ITeslaHolder;
import net.darkhax.tesla.capability.TeslaCapabilities;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockLever;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

@Optional.Interface(iface = "cofh.redstoneflux.api.IEnergyReceiver", modid = "redstoneflux")
public abstract class TileDevice extends CommonTileInventory implements ITickable, ISidedInventory, IEnergyReceiver {

	protected EnergyStorageExt energy = new EnergyStorageExt(80000, 2000);
	protected Map<String, Map<Direction, Mode>> map = Maps.newHashMap();
	protected Map<Integer, Integer> progress = Maps.newHashMap();
	protected boolean split;
	protected double fuel, maxfuel, lastTickFuelUsed;

	public TileDevice() {
		super(13);
		for (int i = 0; i < 3; i++)
			progress.put(i, 0);
		map.put("in", Maps.newHashMap());
		map.put("out", Maps.newHashMap());
		map.put("fuel", Maps.newHashMap());
		for (Direction f : Direction.values())
			for (String k : map.keySet()) {
				map.get(k).put(f, Mode.DISABLED);
			}
		map.get("in").put(Direction.TOP, Mode.ENABLED);
		map.get("out").put(Direction.BOTTOM, Mode.ENABLED);
		map.get("fuel").put(Direction.FRONT, Mode.ENABLED);
		map.get("fuel").put(Direction.LEFT, Mode.ENABLED);
		map.get("fuel").put(Direction.RIGHT, Mode.ENABLED);
		map.get("fuel").put(Direction.BACK, Mode.ENABLED);
	}

	private Map<Upgrade, Integer> cache = null;

	public int getAmount(Upgrade upgrade) {
		if (cache == null) {
			cache = Maps.newEnumMap(Upgrade.class);
			for (Upgrade u : Upgrade.values())
				cache.put(u, 0);
			for (int i = 8; i < 13; i++) {
				ItemStack u = getStackInSlot(i);
				if (u.getItem() == ModItems.upgrade && ModConfig.upgrades.get(Upgrade.values()[u.getItemDamage()]))
					cache.put(Upgrade.values()[u.getItemDamage()], u.getCount());
			}
		}
		return cache.get(upgrade);
	}

	@Override
	public void markDirty() {
		super.markDirty();
		cache = null;
	}

	public int[] getInputSlots() {
		int s = getAmount(Upgrade.SLOT);
		if (s == 0)
			return new int[] { 0 };
		if (s == 1)
			return new int[] { 0, 1 };
		if (s == 2)
			return new int[] { 0, 1, 2 };
		return new int[0];
	}

	public int[] getOutputSlots() {
		int s = getAmount(Upgrade.SLOT);
		if (s == 0)
			return new int[] { 3 };
		if (s == 1)
			return new int[] { 3, 4 };
		if (s == 2)
			return new int[] { 3, 4, 5 };
		return new int[0];
	}

	public int[] getFuelSlots() {
		return new int[] { 6, 7 };
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		energy.setEnergyStored(compound.getInteger("energy"));
		progress = NBTHelper.getMap(compound, "progress", Integer.class, Integer.class);
		split = compound.getBoolean("split");
		fuel = compound.getDouble("fuel");
		maxfuel = compound.getDouble("maxfuel");
		lastTickFuelUsed = compound.getDouble("lastTickFuelUsed");
		map.put("in", NBTHelper.getMap(compound, "inmap", Direction.class, Mode.class));
		map.put("out", NBTHelper.getMap(compound, "outmap", Direction.class, Mode.class));
		map.put("fuel", NBTHelper.getMap(compound, "fuelmap", Direction.class, Mode.class));
		super.readFromNBT(compound);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound.setInteger("energy", energy.getEnergyStored());
		NBTHelper.setMap(compound, "progress", progress);
		compound.setBoolean("split", split);
		compound.setDouble("fuel", fuel);
		compound.setDouble("maxfuel", maxfuel);
		compound.setDouble("lastTickFuelUsed", lastTickFuelUsed);
		NBTHelper.setMap(compound, "inmap", map.get("in"));
		NBTHelper.setMap(compound, "outmap", map.get("out"));
		NBTHelper.setMap(compound, "fuelmap", map.get("fuel"));
		return super.writeToNBT(compound);
	}

	@Override
	public boolean openGUI(EntityPlayerMP player) {
		int i = this instanceof TileFurnus ? 0 : 1;
		player.openGui(Furnus.instance, i, world, getX(), getY(), getZ());
		return true;
	}

	public boolean isSplit() {
		return split;
	}

	public double getFuel() {
		return fuel;
	}

	public double getMaxfuel() {
		return maxfuel;
	}

	public double getLastTickFuelUsed() {
		return lastTickFuelUsed;
	}

	public Map<Integer, Integer> getProgress() {
		return progress;
	}

	public Map<String, Map<Direction, Mode>> getMap() {
		return map;
	}

	@Override
	public int getEnergyStored(EnumFacing from) {
		return energy.getEnergyStored();
	}

	@Override
	public int getMaxEnergyStored(EnumFacing from) {
		return energy.getMaxEnergyStored();
	}

	@Override
	public boolean canConnectEnergy(EnumFacing from) {
		return getAmount(Upgrade.ENERGY) > 0;
	}

	@Override
	public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
		return getAmount(Upgrade.ENERGY) > 0 ? energy.receiveEnergy(maxReceive, simulate) : 0;
	}

	private Direction getDirectionFromSide(EnumFacing side) {
		if (side.getAxis().isVertical())
			return Direction.values()[side.ordinal()];
		EnumFacing face = getBlockState().getValue(BlockDirectional.FACING);
		if (face == EnumFacing.NORTH)
			return Direction.values()[side.ordinal()];
		if (face == EnumFacing.SOUTH)
			return Direction.values()[side.getOpposite().ordinal()];
		if (face == EnumFacing.EAST)
			return Direction.values()[side.rotateYCCW().ordinal()];
		if (face == EnumFacing.WEST)
			return Direction.values()[side.rotateY().ordinal()];
		return null;
	}

	@Override
	public int[] getSlotsForFace(EnumFacing side) {
		if (getAmount(Upgrade.IO) == 0) {
			switch (side) {
			case DOWN:
				return getOutputSlots();
			case UP:
				return getInputSlots();
			default:
				return getFuelSlots();
			}
		}
		Direction dir = getDirectionFromSide(side);
		int ret[] = new int[] {};
		if (map.get("in").get(dir) != Mode.DISABLED)
			ret = Ints.concat(ret, getInputSlots());
		if (map.get("out").get(dir) != Mode.DISABLED)
			ret = Ints.concat(ret, getOutputSlots());
		if (map.get("fuel").get(dir) != Mode.DISABLED)
			ret = Ints.concat(ret, getFuelSlots());
		return ret;
	}

	@Override
	public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing side) {
		Direction dir = getDirectionFromSide(side);
		if ((map.get("in").get(dir) != Mode.DISABLED && Ints.contains(getInputSlots(), index)) || (map.get("fuel").get(dir) != Mode.DISABLED && Ints.contains(getFuelSlots(), index)))
			return isItemValidForSlot(index, itemStackIn);
		return false;
	}

	@Override
	public boolean canExtractItem(int index, ItemStack stack, EnumFacing side) {
		Direction dir = getDirectionFromSide(side);
		if ((map.get("out").get(dir) != Mode.DISABLED && Ints.contains(getOutputSlots(), index)) || (map.get("fuel").get(dir) != Mode.DISABLED && Ints.contains(getFuelSlots(), index) && !TileEntityFurnace.isItemFuel(stack)))
			return true;
		return false;
	}

	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack) {
		if (stack.isEmpty() || Ints.contains(getOutputSlots(), index))
			return false;
		if (Ints.contains(getInputSlots(), index))
			return !getResult(stack).isEmpty();
		if (Ints.contains(getFuelSlots(), index))
			return TileEntityFurnace.isItemFuel(stack);
		return stack.getItem() == ModItems.upgrade && ContainerDevice.slotForUpgrade(index, Upgrade.values()[stack.getItemDamage()], this);
	}

	public abstract ItemStack getResult(ItemStack input);

	@Override
	public void update() {
		output();
		input();
		organizeItems();
		if (fuel > maxfuel)
			maxfuel = fuel;
		if (fuel < 0)
			fuel = 0;
		if (world.getTotalWorldTime() % 6 == 0 && !world.isRemote) {
			if (fuel > 0) {
				((CommonBlock) getBlockType()).changeProperty(world, pos, BlockLever.POWERED, true);
			} else {
				((CommonBlock) getBlockType()).changeProperty(world, pos, BlockLever.POWERED, false);
			}
		}
		fuelUp();
		double tmp = fuel;
		for (int j : getInputSlots())
			burn(j);
		double foo = lastTickFuelUsed;
		lastTickFuelUsed = tmp - fuel;
		if (lastTickFuelUsed == 0. && getAmount(Upgrade.ECO) > 0)
			lastTickFuelUsed = foo;
	}

	public int neededTicks() {
		int val = this instanceof TileFurnus ? 140 : 180;
		val /= (1. + getAmount(Upgrade.SPEED) * ModConfig.speedMultiplier);
		return Math.max(val, 5);
	}

	public double fuelMultiplier() {
		double x = (1. + getAmount(Upgrade.SPEED) * ModConfig.speedFuelMultiplier) * (1. / (1. + getAmount(Upgrade.EFFICIENCY) * ModConfig.effiFuelMultiplier));
		return x;
	}

	private void burn(int i) {
		double neededFuel = 1.;
		neededFuel *= fuelMultiplier();
		neededFuel /= neededTicks() / 200.;
		boolean processed = false;
		if (!canProcess(i)) {
			progress.put(i, 0);
		} else {
			if (fuel >= neededFuel) {
				int progres = progress.get(i) + 1;
				progress.put(i, progres);
				processed = true;
				if (progres >= neededTicks()) {
					processItem(i);
					progress.put(i, 0);
					if (!activePlayers.isEmpty())
						markForSync();
				}
			} else if (getAmount(Upgrade.ECO) == 0) {
				progress.put(i, 0);
			}
		}
		if (processed || getAmount(Upgrade.ECO) == 0)
			fuel -= Math.min(neededFuel, fuel);
	}

	protected void processItem(int slot) {
		if (world.isRemote)
			return;
		ItemStack itemstack = getResult(getStackInSlot(slot));
		if (itemstack.isEmpty())
			return;
		if (getStackInSlot(slot + 3).isEmpty()) {
			setInventorySlotContents(slot + 3, itemstack.copy());
		} else if (ItemHandlerHelper.canItemStacksStack(getStackInSlot(slot + 3), itemstack)) {
			getStackInSlot(slot + 3).grow(itemstack.getCount());
		}
		getStackInSlot(slot).shrink(1);
	}

	private void fuelUp() {
		//		if (world.isRemote) {
		//			return;
		//		}
		int slot0 = getFuelSlots()[0], slot1 = getFuelSlots()[1];
		if (getStackInSlot(slot0).isEmpty() && TileEntityFurnace.isItemFuel(getStackInSlot(slot1)))
			setInventorySlotContents(slot0, removeStackFromSlot(slot1));
		if (!canProcessAny() || fuel > lastTickFuelUsed + 2)
			return;
		ItemStack stack0 = getStackInSlot(slot0);
		int burntime = TileEntityFurnace.getItemBurnTime(stack0);
		if (burntime > 0) {
			if (stack0.getItem().getContainerItem(stack0).isEmpty())
				decrStackSize(slot0, 1);
			else
				setInventorySlotContents(slot0, stack0.getItem().getContainerItem(stack0));
		} else {
			int fac = 25;
			int consume = 1600 * fac;
			burntime = (int) (energy.extractEnergy(consume, false) / (double) fac);
		}
		if (burntime > 0) {
			//			burntime *= (neededTicks() / 200.) + .001;
			fuel += burntime;
			maxfuel = fuel;
			if (!activePlayers.isEmpty())
				markForSync();
		}
	}

	protected boolean canProcess(int slot) {
		if (getStackInSlot(slot).isEmpty()) {
			return false;
		} else {
			ItemStack itemstack = getResult(getStackInSlot(slot));
			if (itemstack.isEmpty())
				return false;
			if (getStackInSlot(slot + 3).isEmpty())
				return true;
			if (!ItemHandlerHelper.canItemStacksStack(itemstack, getStackInSlot(slot + 3)))
				return false;
			int result = getStackInSlot(slot + 3).getCount() + itemstack.getCount();
			return result <= getInventoryStackLimit() && result <= getStackInSlot(slot + 3).getMaxStackSize();
		}
	}

	protected boolean canProcessAny() {
		return Arrays.stream(getInputSlots()).anyMatch(i -> canProcess(i));
	}

	private void output() {
		if (!world.isRemote && getAmount(Upgrade.IO) > 0 && world.getTotalWorldTime() % 10 == 0) {
			for (String s : new String[] { "out", "fuel" }) {
				Map<Direction, Mode> m = map.get(s);
				for (EnumFacing face : EnumFacing.VALUES) {
					Direction dir = getDirectionFromSide(face);
					if (m.get(dir) != Mode.AUTO)
						continue;
					IItemHandler handler = InvHelper.getItemHandler(world, pos.offset(face), face.getOpposite());
					if (handler == null)
						continue;
					IItemHandler that = InvHelper.getItemHandler(world, pos, face);
					if (InvHelper.transfer(that, handler, 2 + getAmount(Upgrade.SLOT) * 2, Predicates.alwaysTrue()))
						break;

				}
			}
		}
	}

	private void input() {
		if (!world.isRemote && getAmount(Upgrade.IO) > 0 && world.getTotalWorldTime() % 10 == 0) {
			for (String s : new String[] { "in", "fuel" }) {
				Map<Direction, Mode> m = map.get(s);
				for (EnumFacing face : EnumFacing.VALUES) {
					Direction dir = getDirectionFromSide(face);
					if (m.get(dir) != Mode.AUTO)
						continue;
					IItemHandler handler = InvHelper.getItemHandler(world, pos.offset(face), face.getOpposite());
					if (handler == null)
						continue;
					IItemHandler that = InvHelper.getItemHandler(world, pos, face);
					Predicate<ItemStack> pred = Predicates.alwaysTrue();
					if (s.equals("fuel"))
						pred = st -> isItemValidForSlot(6, st);
					else
						pred = st -> isItemValidForSlot(0, st);
					if (InvHelper.transfer(handler, that, s.equals("fuel") ? 1 : 2 + getAmount(Upgrade.SLOT) * 2, pred))
						break;

				}
			}
		}
	}

	private void organizeItems() {
		if (world.isRemote || getAmount(Upgrade.SLOT) == 0 || world.getTotalWorldTime() % 8 != 0)
			return;
		if (split) {
			for (int i : getInputSlots()) {
				for (int j : getInputSlots())
					if (i > j) {
						ItemStack stack1 = getStackInSlot(i), stack2 = getStackInSlot(j);
						if (!stack1.isEmpty() || !stack2.isEmpty()) {
							if (stack1.isEmpty()) {
								if (stack2.getCount() <= 1 || !fit(stack2, i))
									continue;
								List<ItemStack> splitted = StackHelper.split(stack2);
								setInventorySlotContents(i, splitted.get(0));
								setInventorySlotContents(j, splitted.get(1));
							} else if (stack2.isEmpty()) {
								if (stack1.getCount() <= 1 || !fit(stack1, j))
									continue;
								List<ItemStack> splitted = StackHelper.split(stack1);
								setInventorySlotContents(i, splitted.get(0));
								setInventorySlotContents(j, splitted.get(1));
							} else {
								if (ItemHandlerHelper.canItemStacksStack(stack1, stack2)) {
									int s = stack1.getCount() + stack2.getCount();
									setInventorySlotContents(i, ItemHandlerHelper.copyStackWithSize(stack1, Utils.split(s, 2).get(0)));
									setInventorySlotContents(j, ItemHandlerHelper.copyStackWithSize(stack1, Utils.split(s, 2).get(1)));
								}
							}
						}
					}
			}
		} else {
			for (int i : getInputSlots()) {
				for (int j : getInputSlots())
					if (getStackInSlot(j).isEmpty() && !getStackInSlot(i).isEmpty() && !canProcess(i) && fit(getStackInSlot(i), j)) {
						setInventorySlotContents(j, getStackInSlot(i).copy());
						setInventorySlotContents(i, ItemStack.EMPTY);
					}
			}
		}
	}

	protected boolean fit(ItemStack stack, int slot) {
		ItemStack result = getResult(stack);
		return getStackInSlot(slot + 3).isEmpty() || (ItemHandlerHelper.canItemStacksStack(result, getStackInSlot(slot + 3)) && getStackInSlot(slot + 3).getCount() + result.getCount() <= getStackInSlot(slot + 3).getMaxStackSize());
	}

	@Override
	public void handleMessage(EntityPlayer player, NBTTagCompound nbt) {
		int id = nbt.getInteger("id");
		if (id == 0)
			split ^= true;
		else {
			int ID = nbt.getInteger("id") - 10;
			map.get(nbt.getString("win")).put(Direction.values()[ID], map.get(nbt.getString("win")).get(Direction.values()[ID]).next());
		}
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		return super.hasCapability(capability, facing) || (getAmount(Upgrade.ENERGY) > 0 && (capability == CapabilityEnergy.ENERGY || (LimeLib.teslaLoaded && (capability == TeslaCapabilities.CAPABILITY_CONSUMER || capability == TeslaCapabilities.CAPABILITY_HOLDER))));
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if (getAmount(Upgrade.ENERGY) > 0) {
			if (capability == CapabilityEnergy.ENERGY)
				return (T) energy;
			if (LimeLib.teslaLoaded && (capability == TeslaCapabilities.CAPABILITY_CONSUMER || capability == TeslaCapabilities.CAPABILITY_HOLDER))
				return (T) new TeslaWrapper(energy);
		}
		return super.getCapability(capability, facing);
	}

	@Optional.InterfaceList(value = { @Optional.Interface(iface = "net.darkhax.tesla.api.ITeslaHolder", modid = "tesla"), @Optional.Interface(iface = "net.darkhax.tesla.api.ITeslaConsumer", modid = "tesla") })
	private static class TeslaWrapper implements ITeslaHolder, ITeslaConsumer {
		private IEnergyStorage storage;

		public TeslaWrapper(IEnergyStorage storage) {
			this.storage = storage;
		}

		@Override
		public long givePower(long power, boolean simulated) {
			return storage.receiveEnergy((int) (power % Integer.MAX_VALUE), simulated);
		}

		@Override
		public long getStoredPower() {
			return storage.getEnergyStored();
		}

		@Override
		public long getCapacity() {
			return storage.getMaxEnergyStored();
		}
	}
}
