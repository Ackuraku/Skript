/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011-2013 Peter Güttinger
 * 
 */

package ch.njol.skript.expressions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.eclipse.jdt.annotation.Nullable;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.util.Math2;
import ch.njol.util.StringUtils;

/**
 * TODO make a 'line %number% of %text%' expression and figure out how to deal with signs (4 lines, delete = empty, etc...)
 * 
 * @author joeuguce99
 */
@Name("Lore")
@Description("An item's lore. ")
@Examples("set the 1st line of the item's lore to \"<orange>Excalibur 2.0\"")
@Since("2.1")
public class ExprLore extends SimpleExpression<String> {
	static {
		try {
			ItemMeta.class.getName();
			
			Skript.registerExpression(ExprLore.class, String.class, ExpressionType.PROPERTY,
					"[the] line %number% of [the] lore of [%itemstack/itemtype%]", "[the] line %number% of %itemstack/itemtype%'[s] lore",
					"[the] %number%(st|nd|rd|th) line of [the] lore of [%itemstack/itemtype%]", "[the] %number%(st|nd|rd|th) line of %itemstack/itemtype%'[s] lore");
			
		} catch (final NoClassDefFoundError e) {}
	}
	
	@SuppressWarnings("null")
	private Expression<Number> line;
	
	@SuppressWarnings("null")
	private Expression<?> item;
	
	@SuppressWarnings({"unchecked", "null"})
	@Override
	public boolean init(final Expression<?>[] exprs, final int matchedPattern, final Kleenean isDelayed, final ParseResult parseResult) {
		line = (Expression<Number>) exprs[0];
		item = exprs[exprs.length - 1];
		return true;
	}
	
	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}
	
	@Override
	@Nullable
	protected String[] get(final Event e) {
		final Object i = item.getSingle(e);
		if (i == null || i instanceof ItemStack && ((ItemStack) i).getType() == Material.AIR)
			return null;
		final ItemMeta meta = i instanceof ItemStack ? ((ItemStack) i).getItemMeta() : (ItemMeta) ((ItemType) i).getItemMeta();
		if (meta == null || !meta.hasLore())
			return null;
		return new String[] {StringUtils.join(meta.getLore(), "\n")};
	}
	
	@Override
	public String toString(final @Nullable Event e, final boolean debug) {
		return "the line " + line.toString(e, debug) + " of the lore of " + item.toString(e, debug);
	}
	
	@Override
	@Nullable
	public Class<?>[] acceptChange(final ChangeMode mode) {
		switch (mode) {
			case SET:
			case DELETE:
			case ADD:
			case REMOVE:
			case REMOVE_ALL:
				return new Class[] {String.class};
			case RESET:
			default:
				return null;
		}
	}
	
	@Override
	public void change(final Event e, final @Nullable Object[] delta, final ChangeMode mode) throws UnsupportedOperationException {
		final Object i = item.getSingle(e);
		if (i == null || i instanceof ItemStack && ((ItemStack) i).getType() == Material.AIR)
			return;
		final Number n = line.getSingle(e);
		if (n == null)
			return;
		final int l = Math2.fit(0, n.intValue() - 1, 99); // TODO figure out the actual maximum
		ItemMeta meta = i instanceof ItemStack ? ((ItemStack) i).getItemMeta() : (ItemMeta) ((ItemType) i).getItemMeta();
		if (meta == null)
			meta = Bukkit.getItemFactory().getItemMeta(Material.STONE);
		final List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
		for (int j = lore.size(); j <= l; j++)
			lore.add("");
		switch (mode) {
			case SET:
				assert delta != null;
				lore.set(l, (String) delta[0]);
				break;
			case DELETE:
				if (l == lore.size())
					lore.remove(lore.size() - 1);
				else
					lore.set(l, "");
				break;
			case ADD:
				assert delta != null;
				lore.set(l, lore.get(l) + (String) delta[0]);
				break;
			case REMOVE:
			case REMOVE_ALL:
				assert delta != null;
				if (SkriptConfig.caseSensitive.value()) {
					lore.set(l, mode == ChangeMode.REMOVE ? lore.get(l).replaceFirst(Pattern.quote((String) delta[0]), "") : lore.get(l).replace((CharSequence) delta[0], ""));
				} else {
					final Matcher m = Pattern.compile(Pattern.quote((String) delta[0]), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(lore.get(l));
					lore.set(l, mode == ChangeMode.REMOVE ? m.replaceFirst("") : m.replaceAll(""));
				}
				break;
			case RESET:
				return;
		}
		meta.setLore(lore.size() == 0 ? null : lore);
		if (i instanceof ItemStack)
			((ItemStack) i).setItemMeta(meta);
		else
			((ItemType) i).setItemMeta(meta);
		return;
	}
	
	@Override
	public boolean isSingle() {
		return true;
	}
	
}
