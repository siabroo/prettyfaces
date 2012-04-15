package org.ocpsoft.prettyfaces.annotation.handlers;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;

import javax.faces.event.PhaseId;

import org.ocpsoft.logging.Logger;
import org.ocpsoft.prettyfaces.annotation.ParameterBinding;
import org.ocpsoft.rewrite.annotation.api.ClassContext;
import org.ocpsoft.rewrite.annotation.api.FieldContext;
import org.ocpsoft.rewrite.annotation.spi.AnnotationHandler;
import org.ocpsoft.rewrite.bind.El;
import org.ocpsoft.rewrite.config.Condition;
import org.ocpsoft.rewrite.config.Visitor;
import org.ocpsoft.rewrite.faces.config.PhaseBinding;
import org.ocpsoft.rewrite.param.Parameterized;

public class ParameterBindingHandler implements AnnotationHandler<ParameterBinding>
{

   private final Logger log = Logger.getLogger(ParameterBindingHandler.class);

   @Override
   public Class<ParameterBinding> handles()
   {
      return ParameterBinding.class;
   }

   @Override
   public void process(ClassContext classContext, AnnotatedElement element, ParameterBinding annotation)
   {

      if (element instanceof Field && classContext instanceof FieldContext) {
         Field field = (Field) element;
         FieldContext context = (FieldContext) classContext;

         // default name is the name of the field
         String param = field.getName();

         // but the name specified in the annotation is preferred
         if (!annotation.value().isEmpty()) {
            param = annotation.value();
         }

         // FIXME: dirty way to build the EL expression
         String simpleClassName = field.getDeclaringClass().getSimpleName();
         String beanName = String.valueOf(simpleClassName.charAt(0)).toLowerCase()
                  + simpleClassName.substring(1);
         String expression = "#{" + beanName + "." + field.getName() + "}";

         // add bindings to conditions by walking over the condition tree
         context.getRuleBuilder().accept(new AddBindingVisitor(context, param, expression));

         if (log.isTraceEnabled()) {
            log.trace("Binding parameter [{}] to EL expression: {}", param, expression);
         }

      }

   }

   private final class AddBindingVisitor implements Visitor<Condition>
   {
      private final String param;
      private final String expression;
      private final FieldContext context;

      public AddBindingVisitor(FieldContext context, String paramName, String expression)
      {
         this.context = context;
         this.param = paramName;
         this.expression = expression;
      }

      @Override
      @SuppressWarnings("rawtypes")
      public void visit(Condition condition)
      {
         if (condition instanceof Parameterized) {
            Parameterized parameterized = (Parameterized) condition;

            // build an deferred EL binding
            El elBinding = El.property(expression);
            PhaseBinding deferredBinding = PhaseBinding.to(elBinding).after(PhaseId.RESTORE_VIEW);

            // add the parameter and the binding
            parameterized.where(param).bindsTo(deferredBinding);

            // register the binding builder in the field context
            context.setBindingBuilder(elBinding);

         }
      }
   }

}
