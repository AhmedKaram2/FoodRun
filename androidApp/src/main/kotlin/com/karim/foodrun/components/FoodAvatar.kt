package com.karim.foodrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.karim.foodrun.shared.Person

@Composable
fun FoodAvatar(
    person: Person,
    modifier: Modifier = Modifier,
    size: Dp = FoodSize.Avatar,
    circular: Boolean = false,
    bordered: Boolean = false,
) {
    val shape: Shape = if (circular) CircleShape else RoundedCornerShape(
        if (size >= FoodSize.AvatarLarge) FoodRadius.Input else FoodRadius.Avatar,
    )
    val type = when {
        size >= FoodSize.AvatarLarge -> FoodType.AvatarLarge
        size <= FoodSize.AvatarSmall -> FoodType.AvatarSmall
        else -> FoodType.Avatar
    }
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = FoodColors.person(person),
                shape = shape,
            )
            .then(
                if (bordered) Modifier.border(
                    width = FoodSize.AvatarBorder,
                    color = FoodColors.Cream,
                    shape = shape,
                )
                else Modifier,
            )
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = person.initial,
            style = type,
            color = FoodColors.Ink,
        )
    }
}
