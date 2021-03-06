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

package ch.njol.skript.conditions;

import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Comparator;
import ch.njol.skript.classes.Comparator.Relation;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.log.ErrorQuality;
import ch.njol.skript.log.RetainingLogHandler;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.registrations.Comparators;
import ch.njol.skript.util.Patterns;
import ch.njol.skript.util.Utils;
import ch.njol.util.Checker;
import ch.njol.util.Kleenean;

/**
 * @author Peter Güttinger
 */
@Name("Comparison")
@Description({"A very general condition, it simply compares two values. Usually you can only compare for equality (e.g. block is/isn't of &lt;type&gt;), " +
		"but some values can also be compared using greater than/less than. In that case you can also test for whether an object is between two others.",
		"Note: This is the only element where not all patterns are shown. It has actually another two sets of similar patters, " +
				"but with <code>(was|were)</code> or <code>will be</code> instead of <code>(is|are)</code> respectively, " +
				"which check different <a href='../expressions/#ExprTimeState'>time states</a> of the first expression."})
@Examples({"the clicked block is a stone slab or a double stone slab",
		"time in the player's world is greater than 8:00",
		"the creature is not an enderman or an ender dragon"})
@Since("1.0")
public class CondCompare extends Condition {
	
	private final static Patterns<Relation> patterns = new Patterns<Relation>(new Object[][] {
			{"%objects% ((is|are) ((greater|more|higher|bigger|larger) than|above)|\\>) %objects%", Relation.GREATER},
			{"%objects% ((is|are) (greater|more|higher|bigger|larger|above) [than] or (equal to|the same as)|(is not|are not|isn't|aren't) ((less|smaller) than|below)|\\>=) %objects%", Relation.GREATER_OR_EQUAL},
			{"%objects% ((is|are) ((less|smaller) than|below)|\\<) %objects%", Relation.SMALLER},
			{"%objects% ((is|are) (less|smaller|below) [than] or (equal to|the same as)|(is not|are not|isn't|aren't) ((greater|more|higher|bigger|larger) than|above)|\\<=) %objects%", Relation.SMALLER_OR_EQUAL},
			{"%objects% ((is|are) (not|neither)|isn't|aren't|!=) [equal to] %objects%", Relation.NOT_EQUAL},
			{"%objects% (is|are|=) [(equal to|the same as)] %objects%", Relation.EQUAL},
			{"%objects% (is|are) between %objects% and %objects%", Relation.EQUAL},
			{"%objects% (is not|are not|isn't|aren't) between %objects% and %objects%", Relation.NOT_EQUAL},
			
			{"%objects@-1% (was|were) ((greater|more|higher|bigger|larger) than|above) %objects%", Relation.GREATER},
			{"%objects@-1% ((was|were) (greater|more|higher|bigger|larger|above) [than] or (equal to|the same as)|(was not|were not|wasn't|weren't) ((less|smaller) than|below)) %objects%", Relation.GREATER_OR_EQUAL},
			{"%objects@-1% (was|were) ((less|smaller) than|below) %objects%", Relation.SMALLER},
			{"%objects@-1% ((was|were) (less|smaller|below) [than] or (equal to|the same as)|(was not|were not|wasn't|weren't) ((greater|more|higher|bigger|larger) than|above)) %objects%", Relation.SMALLER_OR_EQUAL},
			{"%objects@-1% ((was|were) (not|neither)|wasn't|weren't) [equal to] %objects%", Relation.NOT_EQUAL},
			{"%objects@-1% (was|were) [(equal to|the same as)] %objects%", Relation.EQUAL},
			{"%objects@-1% (was|were) between %objects% and %objects%", Relation.EQUAL},
			{"%objects@-1% (was not|were not|wasn't|weren't) between %objects% and %objects%", Relation.NOT_EQUAL},
			
			{"%objects@1% will be ((greater|more|higher|bigger|larger) than|above) %objects%", Relation.GREATER},
			{"%objects@1% (will be (greater|more|higher|bigger|larger|above) [than] or (equal to|the same as)|(will not|won't) be ((less|smaller) than|below)) %objects%", Relation.GREATER_OR_EQUAL},
			{"%objects@1% will be ((less|smaller) than|below) %objects%", Relation.SMALLER},
			{"%objects@1% (will be (less|smaller|below) [than] or (equal to|the same as)|(will not|won't) be ((greater|more|higher|bigger|larger) than|above)) %objects%", Relation.SMALLER_OR_EQUAL},
			{"%objects@1% ((will (not|neither) be|won't be)|(isn't|aren't|is not|are not) (turning|changing) [in]to) [equal to] %objects%", Relation.NOT_EQUAL},
			{"%objects@1% (will be [(equal to|the same as)]|(is|are) (turning|changing) [in]to) %objects%", Relation.EQUAL},
			{"%objects@1% will be between %objects% and %objects%", Relation.EQUAL},
			{"%objects@1% (will not be|won't be) between %objects% and %objects%", Relation.NOT_EQUAL}
	});
	
	static {
		Skript.registerCondition(CondCompare.class, patterns.getPatterns());
	}
	
	@SuppressWarnings("null")
	private Expression<?> first;
	@SuppressWarnings("null")
	Expression<?> second;
	@Nullable
	Expression<?> third;
	@SuppressWarnings("null")
	Relation relation;
	@SuppressWarnings("rawtypes")
	@Nullable
	Comparator comp;
	
	@SuppressWarnings("null")
	@Override
	public boolean init(final Expression<?>[] vars, final int matchedPattern, final Kleenean isDelayed, final ParseResult parser) {
		first = vars[0];
		second = vars[1];
		if (vars.length == 3)
			third = vars[2];
		relation = patterns.getInfo(matchedPattern);
		final boolean b = init();
		final Expression<?> third = this.third;
		if (!b) {
			if (third == null && first.getReturnType() == Object.class && second.getReturnType() == Object.class) {
				return false;
			} else {
				Skript.error("Can't compare " + f(first) + " with " + f(second) + (third == null ? "" : " and " + f(third)), ErrorQuality.NOT_AN_EXPRESSION);
				return false;
			}
		}
		@SuppressWarnings("rawtypes")
		final Comparator comp = this.comp;
		if (comp != null) {
			if (third == null) {
				if (!relation.isEqualOrInverse() && !comp.supportsOrdering()) {
					Skript.error("Can't test " + f(first) + " for being '" + relation + "' " + f(second), ErrorQuality.NOT_AN_EXPRESSION);
					return false;
				}
			} else {
				if (!comp.supportsOrdering()) {
					Skript.error("Can't test " + f(first) + " for being 'between' " + f(second) + " and " + f(third), ErrorQuality.NOT_AN_EXPRESSION);
					return false;
				}
			}
		}
		return true;
	}
	
	public final static String f(final Expression<?> e) {
		if (e.getReturnType() == Object.class)
			return e.toString(null, false);
		return Classes.getSuperClassInfo(e.getReturnType()).getName().withIndefiniteArticle();
	}
	
	@SuppressWarnings("unchecked")
	private boolean init() {
		final RetainingLogHandler log = SkriptLogger.startRetainingLog();
		Expression<?> third = this.third;
		try {
			if (first.getReturnType() == Object.class) {
				final Expression<?> e = first.getConvertedExpression(Object.class);
				if (e == null) {
					log.printErrors();
					return false;
				}
				first = e;
			}
			if (second.getReturnType() == Object.class) {
				final Expression<?> e = second.getConvertedExpression(Object.class);
				if (e == null) {
					log.printErrors();
					return false;
				}
				second = e;
			}
			if (third != null && third.getReturnType() == Object.class) {
				final Expression<?> e = third.getConvertedExpression(Object.class);
				if (e == null) {
					log.printErrors();
					return false;
				}
				this.third = third = e;
			}
			log.printLog();
		} finally {
			log.stop();
		}
		
		final Class<?> f = first.getReturnType(), s = third == null ? second.getReturnType() : Utils.getSuperType(second.getReturnType(), third.getReturnType());
		if (f == Object.class || s == Object.class)
			return true;
		comp = Comparators.getComparator(f, s);
		
		return comp != null;
	}
	
	/*
	 * # := condition (e.g. is, contains, is enchanted with, etc.)
	 * !# := not #
	 * 
	 * a and b # x === a # x && b # x
	 * a or b # x === a # x || b # x
	 * a # x and y === a # x && a # y
	 * a # x or y === a # x || a # y
	 * a and b # x and y === a # x and y && b # x and y === a # x && a # y && b # x && b # y
	 * a and b # x or y === a # x or y && b # x or y
	 * a or b # x and y === a # x and y || b # x and y
	 * a or b # x or y === a # x or y || b # x or y
	 * 
	 * a and b !# x === a !# x && b !# x
	 * a or b !# x === a !# x || b !# x
	 * a !# x and y === a !# x && a !# y
	 * a !# x or y === a !# x || a !# y
	 * a and b !# x and y === a !# x and y && b !# x and y === a !# x && a !# y && b !# x && b !# y
	 * a and b !# x or y === a !# x or y && b !# x or y
	 * a or b !# x and y === a !# x and y || b !# x and y
	 * a or b !# x or y === a !# x or y || b !# x or y
	 */
	@Override
	public boolean check(final Event e) {
		final Expression<?> third = this.third;
		return first.check(e, new Checker<Object>() {
			@Override
			public boolean check(final Object o1) {
				return second.check(e, new Checker<Object>() {
					@Override
					public boolean check(final Object o2) {
						if (third == null)
							return relation.is(comp != null ? comp.compare(o1, o2) : Comparators.compare(o1, o2));
						return third.check(e, new Checker<Object>() {
							@Override
							public boolean check(final Object o3) {
								return relation == Relation.NOT_EQUAL ^
										(Relation.GREATER_OR_EQUAL.is(comp != null ? comp.compare(o1, o2) : Comparators.compare(o1, o2))
										&& Relation.SMALLER_OR_EQUAL.is(comp != null ? comp.compare(o1, o3) : Comparators.compare(o1, o3)));
							}
						});
					}
				});
			}
		});
	}
	
	@Override
	public String toString(final @Nullable Event e, final boolean debug) {
		String s;
		final Expression<?> third = this.third;
		if (third == null)
			s = first.toString(e, debug) + " is " + relation + " " + second.toString(e, debug);
		else
			s = first.toString(e, debug) + " is" + (relation == Relation.EQUAL ? "" : " not") + " between " + second.toString(e, debug) + " and " + third.toString(e, debug);
		if (debug)
			s += " (comparator: " + comp + ")";
		return s;
	}
	
}
