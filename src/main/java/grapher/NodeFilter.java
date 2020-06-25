package grapher;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import scala.Char;

public class NodeFilter {
	private final Expression expression;
	private final List<Token> tokens;
	private enum ComparisonOp {
		Eq("="), LT("<"), GT(">"), LTE(">="), GTE(">="), Like("~"), Ne("!="); 
		final String value;
		private ComparisonOp(String value) {
			this.value=value;
		}
		@Override 
		public String toString() {
			return value;
		}
	};
	/**
	 * 
	 * @param query
	 * @throws IllegalArgumentException if the query is bad.
	 */
	public NodeFilter(String query, NodeProperties nodeProperties) {
		tokens = tokenize(query);
		expression =parse();
	}
	private static class ExpressionIndex {
		final Expression expression;
		final int indexExclusive;
		public ExpressionIndex(Expression expr, int indexExclusive) {
			this.expression=expr;
			this.indexExclusive=indexExclusive;
		}
	}
	/*
	 * E    -> Disj 
	 * Disj -> Conj | Conj Or Disj
	 * Conj -> EqualityOrComparison | Equality And Conj |  Not Conj  | LParen E RParen
	 * EqualityOrComparison -> Identifier Eq StringToken | Identifier Eq NullToken | 
	 *    Identifier ( Eq | LT | GT | LTE | GTE) DoubleToken | Identifier ( Eq | LT | GT | LTE | GTE)LongToken
	 */
	private Expression parse() {
		ExpressionIndex ei = parseExpression(0, tokens.size());
		if (ei.indexExclusive == tokens.size()) {
			return ei.expression;
		} else {
			throw new IllegalArgumentException("Could not parse " + tokens);
		}
	}
	// end index is the limit
	private ExpressionIndex parseExpression(int startIndex, int endIndexExclusive) {
		return parseDisjunction(startIndex, endIndexExclusive);
	}
	private ExpressionIndex parseDisjunction(int startIndex, int endIndexExclusive) {
		ExpressionIndex ei = parseConjunction(startIndex, endIndexExclusive);
		if (ei.indexExclusive == endIndexExclusive) {
			return ei;
		}
		if (tokens.get(ei.indexExclusive) instanceof OrToken) {
			ExpressionIndex ei2 = parseDisjunction(ei.indexExclusive+1, endIndexExclusive);
			Or or = new Or(ei.expression, ei2.expression);
			return new ExpressionIndex(or,ei2.indexExclusive);
		}
		return ei;
	}
// Conj -> (EqualityOrComparison | Not Conj | LParen E RParen) [And Conj]
	private ExpressionIndex parseConjunction(int startIndex, int endIndexExclusive) {
		final Token token = tokens.get(startIndex);
		if (token instanceof LParen) {
			int rparenIndex = findMatchingRParenIndex(startIndex+1);
			if (rparenIndex<0) {
				throw new IllegalArgumentException("No matching ) at index " + startIndex);
			}
			ExpressionIndex ei = parseExpression(startIndex+1, rparenIndex);
			ExpressionIndex ei2 = new ExpressionIndex(ei.expression, rparenIndex+1);
			return parseConjunctionRest(ei2, endIndexExclusive);
		} else if (token instanceof NotToken) {
			ExpressionIndex ei = parseConjunction(startIndex+1, endIndexExclusive);
			Expression not = new Not(ei.expression);
			return parseConjunctionRest(new ExpressionIndex(not, ei.indexExclusive), endIndexExclusive);
		} else {
			ExpressionIndex ei = parseEqualityOrComparison(startIndex,endIndexExclusive);
			return parseConjunctionRest(new ExpressionIndex(ei.expression, ei.indexExclusive), endIndexExclusive);
		}
	}
	private ExpressionIndex parseEqualityOrComparison(int startIndex, int endIndexExclusive) {
		if (tokens.get(startIndex) instanceof IdentifierToken && startIndex+2 < endIndexExclusive) {
			Token op = tokens.get(startIndex+1);
			if (op instanceof EqToken || op instanceof NeToken || op instanceof LTToken || op instanceof GTToken 
					|| op instanceof LTEqToken || op instanceof GTEqToken || op instanceof SquigglyLikeToken) {
				Token secondArg = tokens.get(startIndex+2);
				if (secondArg instanceof IdentifierToken) {
					secondArg = new StringToken(((IdentifierToken) secondArg).identifier);
				}
				if (secondArg instanceof StringToken || secondArg instanceof LongToken || secondArg instanceof DoubleToken || secondArg instanceof NullToken) {
					Expression expr = new Comparison(getComparisonOp(op), (IdentifierToken)tokens.get(startIndex), secondArg);
					return new ExpressionIndex(expr, startIndex+3);
				}
			}
		}
		throw new IllegalArgumentException("Unable to find equality at index " + startIndex);
	}
	private ComparisonOp getComparisonOp(Token op) {
		if (op instanceof EqToken) {
			return ComparisonOp.Eq;
		} else if (op instanceof NeToken) {
			return ComparisonOp.Ne;
		} else if (op instanceof LTToken) {
			return ComparisonOp.LT;
		} else if (op instanceof GTToken) {
			return ComparisonOp.GT;
		} else if (op instanceof LTEqToken) {
			return ComparisonOp.LTE;
		} else if (op instanceof GTEqToken) {
			return ComparisonOp.GTE;
		} else if (op instanceof SquigglyLikeToken) {
			return ComparisonOp.Like;
		}
		throw new IllegalStateException("op = "+ op);
	}
	private ExpressionIndex parseConjunctionRest(ExpressionIndex ei,  int endIndexExclusive) {
		if (ei.indexExclusive==endIndexExclusive) {
			return ei;
		}
		if (tokens.get(ei.indexExclusive) instanceof AndToken) {
			ExpressionIndex restEi = parseConjunction(ei.indexExclusive+1, endIndexExclusive);
			Expression and = new And(ei.expression, restEi.expression);
			return new ExpressionIndex(and, restEi.indexExclusive);
		}
		return ei;
	}
	//--------------------
	/**
	 * 
	 * @param index  such that tokens.get(index-1) is LParen
	 * @return
	 */
	private int findMatchingRParenIndex(int index) {
		while (index< tokens.size()) {
			Token token = tokens.get(index);
			if (token instanceof LParen) {
				int tmp=findMatchingRParenIndex(index+1);
				if (tmp<0) {
					return -1;
				} else {
					index=tmp+1;
				}
			} else if (token instanceof RParen) {
				return index;
			}
		}
		return -1;
	}
	//--------------------
	public boolean shouldInclude(Node3D node) {
		return expression.passes(node);
	}
	public static abstract class Expression {
		public abstract boolean passes(Node3D node);
	}
	// If token is NullToken, returns null, otherwise returns a Number if Op is not Eq
	private static Object normalize(ComparisonOp op, Token token) {
		Object tokenObject = token.getObject();
		if (token instanceof NullToken) {
			return null;
		}
		if (tokenObject instanceof Long || tokenObject instanceof Double || tokenObject instanceof Integer) {
			return tokenObject;
		}
		switch (op) {
		case Like:
			return token.getObject().toString();
		case Eq: case Ne:
			return token.getObject();
		case GT:
		case GTE:
		case LT:
		case LTE:
			String string = token.getObject().toString();
			if (string.contains(".")) {
				return Double.parseDouble(tokenObject.toString());
			} else {
				return Long.parseLong(tokenObject.toString());
			}
		default:
			throw new IllegalStateException();
		}
	}
	public static class Comparison extends Expression {
		final ComparisonOp op;
		final IdentifierToken identifier;
		final Object expectedObject; // This will be a Number if op is not Eq
		final String expectedObjectString;
		final Pattern pattern;
		public Comparison(ComparisonOp op, IdentifierToken identifier, Token other) {
			this.op = op;
			this.identifier= identifier;
			this.expectedObject = normalize(op, other);
			this.expectedObjectString = expectedObject==null? null: expectedObject.toString();
			this.pattern = op.equals(ComparisonOp.Like) ? makePattern(expectedObjectString) : null;
		}
	
		@Override
		public String toString() {
			return "(" + identifier.toString() + " " + op + " " + expectedObject + ")";
		}
		@Override
		public boolean passes(Node3D node) {
			Object value = node.getProperties().get(identifier.getObject());
			if (expectedObject ==null) {
				return value==null;
			}
			if (value==null) {
				return false;
			}
			if (op.equals(ComparisonOp.Eq)) {
				if (expectedObject instanceof String) {
					return value.toString().equals(expectedObject);
				} else if (expectedObject instanceof Number) {
					return value.toString().equals(expectedObjectString);
				}
			} else if (op.equals(ComparisonOp.Ne)) {
				if (expectedObject instanceof String) {
					return !(value.toString().equals(expectedObject));
				} else if (expectedObject instanceof Number) {
					return !(value.toString().equals(expectedObjectString));
				}
			} else if (op.equals(ComparisonOp.Like)) {
				Matcher matcher = pattern.matcher(value.toString());
				return matcher.matches();
			}
			// op is one of LT, GT, LTE, or GTE
			if (value instanceof String) {
				value=Double.parseDouble(value.toString());
			}
			double nodeValue = ((Number) value).doubleValue();
			double expectedValue = ((Number) expectedObject).doubleValue();
			switch (op) {
			case GT:
				return nodeValue > expectedValue;
			case GTE:
				return nodeValue >= expectedValue;
			case LT:
				return nodeValue < expectedValue;
			case LTE:
				return nodeValue <= expectedValue;
			default:
				throw new IllegalStateException();
			}
		}
	}
	// "*abc*"
	private static Pattern makePattern(String matchString) {
		StringBuilder sb = new StringBuilder();
		for(int i=0;i<matchString.length();i++) {
			char ch=matchString.charAt(i);
			if (ch=='*') {
				sb.append(".*");
			} else if (ch == '?') {
				sb.append(".");
			} else {
				sb.append(ch);
			}
		}
		return Pattern.compile(sb.toString());
	}
	public static class Or extends Expression {
		final Expression expr1;
		final Expression expr2;
		public Or(Expression expr1, Expression expr2) {
			this.expr1 = expr1;
			this.expr2 = expr2;
		}
		@Override
		public String toString() {
			return "(" + expr1 + " Or " + expr2 + ")";
		}
		@Override
		public boolean passes(Node3D node) {
			return expr1.passes(node) || expr2.passes(node);
		}
	}
	public static class And extends Expression {
		final Expression expr1;
		final Expression expr2;
		public And(Expression expr1, Expression expr2) {
			this.expr1 = expr1;
			this.expr2 = expr2;
		}
		@Override
		public String toString() {
			return "(" + expr1 + " And " + expr2 + ")";
		}
		@Override
		public boolean passes(Node3D node) {
			return expr1.passes(node) && expr2.passes(node);
		}
	}
	public static class Not extends Expression {
		final Expression expr;
		public Not(Expression expr) {
			this.expr = expr;
		}
		@Override
		public String toString() {
			return " Not (" + expr + ")";
		}
		@Override
		public boolean passes(Node3D node) {
			return ! expr.passes(node);
		}
	}
	//....
	private static abstract class Token {public Object getObject() {throw new IllegalStateException();}}
	private static class LParen extends Token {@Override public String toString() { return "(";}}
	private static class RParen extends Token {@Override public String toString() { return ")";}}
	private static class IdentifierToken extends Token {
		final String identifier;
		public IdentifierToken(String identifier) {
			this.identifier = identifier;
		}
		@Override
		public String toString() {
			return identifier;
		}
		@Override
		public Object getObject() {
			return identifier;
		}
	}
	private static class NeToken extends Token {@Override public String toString() { return "!=";}}
	private static class OrToken extends Token {@Override public String toString() { return "Or";}}
	private static class AndToken extends Token {@Override public String toString() { return "And";}}
	private static class NotToken extends Token {@Override public String toString() { return "Not";}}
	private static class EqToken extends Token {@Override public String toString() { return "=";}}
	private static class LTToken extends Token {@Override public String toString() { return "<";}}
	private static class LTEqToken extends Token {@Override public String toString() { return ">";}}
	private static class GTToken extends Token {@Override public String toString() { return "<=";}}
	private static class GTEqToken extends Token {@Override public String toString() { return ">=";}}
	private static class SquigglyLikeToken extends Token {@Override public String toString() { return "~";}}
	private static class NullToken extends Token {
		@Override public String toString() { return "Null";}
		@Override public Object getObject() { return null;}
		}
	private static class StringToken extends Token {
		final String string;
		public StringToken(String string) {
			this.string=string;
		}
		@Override 
		public String toString() {
			return string;
		}
		@Override
		public Object getObject() {
			return string;
		}
	}
	private static class LongToken extends Token {
		final long aLong;
		public LongToken(long aLong) {
			this.aLong = aLong;
		}
		@Override 
		public String toString() {
			return ""+aLong;
		}
		@Override
		public Object getObject() {
			return aLong;
		}
	}
	private static class DoubleToken extends Token {
		final double v;
		public DoubleToken(double v) {
			this.v=v;
		}
		@Override 
		public String toString() {
			return ""+v;
		}
		@Override
		public Object getObject() {
			return v;
		}
	}
	private List<Token> tokenize(String query) {
		final List<Token> tokens = new ArrayList<>();
		int index=0;
		while (index<query.length()) {
			char ch=query.charAt(index);
			if (Character.isWhitespace(ch))  {
				index++;
			} else if (Character.isDigit(ch)) {
				index=addNumberToken(ch,index, query, tokens);
			} else if (ch == '(') {
				tokens.add(new LParen());
				index++;
			} else if (ch == ')') {
				tokens.add(new RParen());
				index++;
			} else if (ch== '"' || ch == '\'') {
				index = addStringToken(index+1,query,tokens, ch);
			} else if (ch == '~') {
				tokens.add(new SquigglyLikeToken());
				index++;
			} else if (ch == '=') {
				tokens.add(new EqToken());
				index++;
			} else if (ch == '!') {
				index++;
				if (index==query.length()) {
					throw new IllegalArgumentException("Illegal ! at end");
				}
				ch = query.charAt(index);
				if (ch == '=') {
					index++;
					tokens.add(new NeToken());
				}
			} else if (ch == '<') {
				index++;
				if (index==query.length()) {
					throw new IllegalArgumentException("Illegal < at end");
				}
				ch=query.charAt(index);
				if (ch=='=') {
					index++;
					tokens.add(new LTEqToken());
				} else {
					tokens.add(new LTToken());
				}
			} else if (ch == '>') {
				index++;
				if (index==query.length()) {
					throw new IllegalArgumentException("Illegal > at end");
				}
				ch=query.charAt(index);
				if (ch=='=') {
					index++;
					tokens.add(new GTEqToken());
				} else {
					tokens.add(new GTToken());
				}				
			} else if (ch == '=') {
				tokens.add(new EqToken());
				index++;
				if (index==query.length()) {
					throw new IllegalArgumentException("Illegal = at end");
				}
				if (query.charAt(index)=='=') { // ignoring second =
					index++;
				}
			} else if (Character.isAlphabetic(ch)) {
				index = addIdentifierTokenOrAndOrOrOrNotOrNull(ch,index, query,tokens);
			}
		}
		return tokens;
	}
	private int addIdentifierTokenOrAndOrOrOrNotOrNull(char ch, int index, String query, List<Token> tokens) {
		assert(ch == query.charAt(index));
		final StringBuilder sb = new StringBuilder();
		sb.append(ch);
		index++;
		while (index<query.length()) {
			ch = query.charAt(index);
			if (!Character.isLetterOrDigit(ch)) {
				break;
			}
			sb.append(ch);
			index++;
		}
		String token = sb.toString();
		switch (token.toLowerCase()) {
			case "or": tokens.add(new OrToken()); break;
			case "and": tokens.add(new AndToken()); break;
			case "not": tokens.add(new NotToken()); break;
			case "null": tokens.add(new NullToken()); break;
			default:
				tokens.add(new IdentifierToken(token));
		}
		return index;
	}
	// The char at index-1 was "
	private int addStringToken(final int startIndex, String query, List<Token> tokens, final char delimiterChar) {
		final StringBuilder sb = new StringBuilder();
		int index=startIndex;
		boolean sawCloseDoubleQuote=false;
		while (index<query.length()) {
			char ch= query.charAt(index);
			if (ch== delimiterChar) {
				index++;
				sawCloseDoubleQuote=true;
				break;
			}
			sb.append(ch);
			index++;
		}
		if (!sawCloseDoubleQuote) {
			throw new IllegalArgumentException("Unclosed string literal at index " + startIndex);
		}
		tokens.add(new StringToken(sb.toString()));
		return index;
	}
	// Adds either LongToken or DoubleToken to tokens
	private int addNumberToken(char ch, int index, String query, List<Token> tokens) {
		assert(ch == query.charAt(index));
		StringBuilder sb = new StringBuilder();
		sb.append(ch);
		index++;
		boolean sawPeriod = false;
		while (index<query.length()) {
			ch = query.charAt(index);
			if (ch== '.') {
				if (sawPeriod) {
					throw new IllegalArgumentException("Illegal period at index " + index);
				}
				sawPeriod=true;
				sb.append(ch);
				index++;
			} else if (Character.isDigit(ch)) {
				sb.append(ch);
				index++;
			} else {
				break;
			}
		}
		if (sawPeriod) {
			tokens.add(new DoubleToken(Double.parseDouble(sb.toString())));
		} else {
			tokens.add(new LongToken(Long.parseLong(sb.toString())));
		}
		return index;
	}
	//----
	public static void test1() {
		NodeFilter nodeFilter1 = new NodeFilter("abc = 123 and str4 = 'hello' and str4=hello or xyz > 5 and zzz <= 4", null);
		System.out.println(nodeFilter1.tokens);
		System.out.println(nodeFilter1.expression);
		Node3D node1 = new Node3D("1");
		node1.getProperties().put("str4", "hello");
		node1.getProperties().put("abc", 123);
		node1.getProperties().put("xyz", 1);
		node1.getProperties().put("zzz", 5);
		if (!nodeFilter1.expression.passes(node1)) {
			throw new IllegalStateException();
		}
		node1.getProperties().put("abc", "other");
		if (nodeFilter1.expression.passes(node1)) {
			throw new IllegalStateException();
		}
		node1.getProperties().put("xyz", 6);
		node1.getProperties().put("zzz", 1);
		if (!nodeFilter1.expression.passes(node1)) {
			throw new IllegalStateException();
		}
		
		NodeFilter nodeFilter2 = new NodeFilter("abc = null", null);
		System.out.println(nodeFilter2.tokens);
		System.out.println(nodeFilter2.expression);
		Node3D node2 = new Node3D("2");
		node2.getProperties().put("abc","hello");
		if (nodeFilter2.expression.passes(node2)) {
			throw new IllegalStateException();
		}
		node2.getProperties().remove("abc");
		if (!nodeFilter2.expression.passes(node2)) {
			throw new IllegalStateException();
		}
	}
	private static void test2() {
		NodeFilter nodeFilter = new NodeFilter("test > 0.2",null);
		System.out.println(nodeFilter.tokens);
	}
	public static void main(String [] args) {
		try {
			test1();
		} catch (Throwable thr) {
			thr.printStackTrace();
		}
	}
	}
