import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.material.MaterialData
import ru.myxomopx.mimic.MimicChestService

MIMIC = new MimicChestService(workspace)

def recipe = new ShapelessRecipe(MIMIC.getMimicItem())
recipe.addIngredient(Material.CHEST)
recipe.addIngredient(3,new MaterialData(Material.SKULL_ITEM,1 as byte))
Bukkit.addRecipe(recipe)

null;