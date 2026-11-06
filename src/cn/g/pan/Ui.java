package cn.g.pan;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
final class Ui {
 static final int BG=Color.rgb(243,246,252),TEXT=Color.rgb(20,34,61),MUTED=Color.rgb(100,116,139),BLUE=Color.rgb(37,99,235);
 static int dp(Context c,float d){return (int)(c.getResources().getDisplayMetrics().density*d+.5f);}
 static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
 static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 static TextView text(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(c,3),1);return t;}
 static GradientDrawable shape(int color,int radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 static LinearLayout card(Context c){LinearLayout l=column(c);l.setPadding(dp(c,17),dp(c,16),dp(c,17),dp(c,16));l.setBackground(shape(Color.WHITE,18,c));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(c,13);l.setLayoutParams(p);return l;}
 static Button button(Context c,String label,boolean primary,View.OnClickListener click){Button b=new Button(c);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:BLUE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?BLUE:Color.rgb(232,239,255)));b.setMinHeight(dp(c,46));b.setOnClickListener(click);return b;}
 static void gap(LinearLayout l,int d){Space s=new Space(l.getContext());l.addView(s,new LinearLayout.LayoutParams(1,dp(l.getContext(),d)));}
 static void bold(TextView t){t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);}
 static void insets(View root){root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});}
 static void toast(Context c,String s){Toast.makeText(c,s,Toast.LENGTH_LONG).show();}
}
