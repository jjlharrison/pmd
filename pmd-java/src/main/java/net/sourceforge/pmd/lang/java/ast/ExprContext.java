/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.ast;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.sourceforge.pmd.annotation.Experimental;
import net.sourceforge.pmd.lang.java.types.JPrimitiveType;
import net.sourceforge.pmd.lang.java.types.JTypeMirror;
import net.sourceforge.pmd.lang.java.types.OverloadSelectionResult;

/**
 * Context of an expression. This determines its target type, which is necessary for overload resolution.
 */
@Experimental
public abstract class ExprContext {
    // note: most members of this class are quite low-level and should
    // stay package-private for exclusive use by PolyResolution.

    private ExprContext() {
        // sealed class
    }

    /**
     * Returns the target type, or null if there is none or it couldn't
     * be determined reliably.
     */
    // note: only meant for public use by rules
    // note: this may triggers type resolution of the context
    public @Nullable JTypeMirror getTargetType() {
        return getTargetTypeAfterResolution();
    }

    /**
     * If true this is an invocation context. This means, the target
     * type may depend on overload resolution.
     */
    public boolean isInvocationContext() {
        return false;
    }

    public boolean canGiveContextToPoly(boolean lambda) {
        return true;
    }

    public abstract boolean flowsThroughConditionalBranches();

    static ExprContext newAssignmentCtx(JTypeMirror targetType) {
        return new RegularCtx(targetType, CtxKind.Assignment);
    }

    static ExprContext newNumericCtx(JPrimitiveType targetType) {
        return new RegularCtx(targetType, CtxKind.Numeric);
    }

    static ExprContext newCastCtx(JTypeMirror targetType) {
        return new RegularCtx(targetType, CtxKind.Cast);
    }

    static ExprContext newSuperCtorCtx(JTypeMirror superclassType) {
        return new RegularCtx(superclassType, CtxKind.Other);
    }

    abstract @Nullable JTypeMirror getTargetTypeAfterResolution();

    static final class InvocCtx extends ExprContext {

        final int arg;
        final InvocationNode node;

        InvocCtx(int arg, InvocationNode node) {
            this.arg = arg;
            this.node = node;
        }

        @Override
        public boolean flowsThroughConditionalBranches() {
            return true;
        }

        @Override
        @Nullable JTypeMirror getTargetTypeAfterResolution() {
            // this triggers type resolution of the enclosing expr.
            OverloadSelectionResult overload = node.getOverloadSelectionInfo();
            if (overload.isFailed()) {
                return null;
            }
            return overload.ithFormalParam(arg);
        }

        @Override
        public boolean isInvocationContext() {
            return true;
        }
    }

    /**
     * Kind of a {@link RegularCtx}. Note that this enum does not include
     * invocation contexts because they're handled by {@link InvocCtx}.
     * If we make this public, it should include it.
     */
    enum CtxKind {
        /**
         * Assignment context, eg:
         * <ul>
         * <li>RHS of an assignment
         * <li>Return statement
         * <li>Array initializer
         * </ul>
         *
         * <p>An assignment context flows through ternary/switch branches.
         */
        Assignment,

        /**
         * Cast context. Lambdas and method refs can use them as a
         * target type, but no other expressions. Cast contexts do not
         * flow through ternary/switch branches.
         */
        Cast,

        /**
         * Numeric context. Does not provide a target type for poly expressions.
         * For standalone expressions, they may determine that an (un)boxing or
         * primitive widening conversion occurs. Numeric contexts do not flow
         * through ternary/switch branches.
         *
         * <p>For instance:
         * <pre>{@code
         * Integer integer;
         *
         * array[integer] // Integer is unboxed to int
         * integer + 1    // Integer is unboxed to int
         * 0 + 1.0        // int (left) is widened to double
         * integer + 1.0  // Integer is unboxed to int, then widened to double
         * }</pre>
         */
        Numeric,

        /** Other kinds of situation that have a target type (eg {@link RegularCtx#NO_CTX}). */
        Other,
    }

    static final class RegularCtx extends ExprContext {

        static final RegularCtx NO_CTX = new RegularCtx(null, CtxKind.Other);

        final @Nullable JTypeMirror targetType;
        final CtxKind kind;

        RegularCtx(@Nullable JTypeMirror targetType, CtxKind kind) {
            this.targetType = targetType;
            this.kind = kind;
        }

        @Override
        public boolean flowsThroughConditionalBranches() {
            return kind == CtxKind.Assignment;
        }

        @Override
        public boolean canGiveContextToPoly(boolean lambdaOrMethodRef) {
            return kind == CtxKind.Cast ? lambdaOrMethodRef
                                        : kind == CtxKind.Assignment;
        }

        /**
         * Returns the target type bestowed by this context ON A POLY EXPRESSION.
         *
         * @param allowCasts Whether cast contexts should be considered,
         *                   if false, and this is a cast ctx, returns null.
         */
        @Nullable JTypeMirror getPolyTargetType(boolean allowCasts) {
            if (!allowCasts && kind == CtxKind.Cast || kind == CtxKind.Numeric) {
                return null;
            }
            return targetType;
        }

        @Override
        @Nullable JTypeMirror getTargetTypeAfterResolution() {
            return targetType;
        }
    }
}
