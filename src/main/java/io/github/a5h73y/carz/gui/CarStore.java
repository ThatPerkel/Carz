package io.github.a5h73y.carz.gui;

import de.themoep.inventorygui.GuiElementGroup;
import de.themoep.inventorygui.InventoryGui;
import de.themoep.inventorygui.StaticGuiElement;
import io.github.a5h73y.carz.Carz;
import io.github.a5h73y.carz.model.CarDetails;
import io.github.a5h73y.carz.purchases.CarPurchase;
import io.github.a5h73y.carz.utility.TranslationUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Car Store Gui.
 */
public class CarStore extends AbstractMenu {

	@Override
	public String getTitle() {
		return TranslationUtils.getTranslation("CarStore.Heading", false);
	}

	@Override
	public String[] getGuiSetup() {
		return new String[] {
				TranslationUtils.getTranslation("CarStore.Setup.Line1", false),
				TranslationUtils.getTranslation("CarStore.Setup.Line2", false),
				TranslationUtils.getTranslation("CarStore.Setup.Line3", false)
		};
	}

	@Override
	public GuiElementGroup getGroupContent(InventoryGui parent, Player player) {
		GuiElementGroup group = new GuiElementGroup('g');
		List<Map.Entry<String, CarDetails>> results = new LinkedList<Map.Entry<String, CarDetails>>(Carz.getInstance().getCarController().getCarTypes().entrySet());

		// Remove non buyable vehicles
		results.removeIf(el -> !el.getValue().isBuyable());
		
		// Sort the list by price
		Collections.sort(results, new Comparator<Map.Entry<String, CarDetails>>() {
			public int compare(Map.Entry<String, CarDetails> u1, Map.Entry<String, CarDetails> u2) {
				Double cost1 = Carz.getDefaultConfig().getDouble("CarTypes." + u1.getKey() + ".Cost");
				Double cost2 = Carz.getDefaultConfig().getDouble("CarTypes." + u2.getKey() + ".Cost");
				return cost1.compareTo(cost2);
			}
		});

		for (Map.Entry<String, CarDetails> carType : results) {
			double cost = Carz.getDefaultConfig().getDouble("CarTypes." + carType.getKey() + ".Cost");
			String displayCost = Carz.getInstance().getEconomyApi().getCurrencyName(cost) + cost;
			// Escape $ because of this code's stupidity
			displayCost.replace("$", "\\\\$");
			CarDetails details = carType.getValue();
			group.addElement(
					new StaticGuiElement('e',
							new ItemStack(Material.MINECART),
							click -> {
								Carz.getInstance().getEconomyApi().requestPurchase(
										player, new CarPurchase(carType.getKey()));
								parent.close();
								return true;
							},

							// the car type heading
							String.valueOf(details.getName()),

							// Lore
							String.valueOf(details.getLore()),

							// maximum speed
							TranslationUtils.getValueTranslation("CarDetails.MaxSpeed",
									String.valueOf(details.getStartMaxSpeed()*Carz.speed_conv), false),

							// acceleration
							TranslationUtils.getValueTranslation("CarDetails.Acceleration",
									String.valueOf(details.getAcceleration()), false),

							// fuel usage
							TranslationUtils.getValueTranslation("CarDetails.FuelUsage",
									String.valueOf(details.getFuelUsage()), false),
							" ",
							// economy cost
							TranslationUtils.getValueTranslation("CarDetails.Cost",
									displayCost, false)
					));
		}
		return group;
	}
}
